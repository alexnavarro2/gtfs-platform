# GTFS Platform — Análisis Arquitectónico (Fase 0)

Estado: **decisiones tomadas, en implementación**. Este documento fija las decisiones de arquitectura antes de escribir código y se actualizará si algo cambia durante el desarrollo.

Investigación realizada contra fuentes primarias (no memoria): README y `pom.xml` de `conveyal/gtfs-lib` (rama `master`), repo `MobilityData/gtfs-validator` (USAGE.md, releases), y `gtfs.org/schedule/reference` (spec vigente).

---

## A. Análisis de `conveyal/gtfs-lib`

**Qué hace.** Librería Java para cargar/guardar feeds GTFS de tamaño arbitrario usando almacenamiento respaldado en disco (MapDB) durante la carga, con validación sintáctica y semántica opcional, y persistencia final en PostgreSQL. Es el motor de importación que usa Conveyal Data Tools internamente.

**Arquitectura.** Carga un ZIP fila por fila, tolera errores (no aborta ante un campo corrupto, los reporta), y aplica una fase de validación semántica post-carga (fechas, referencias cruzadas, geometría). No modela un dominio relacional propio de "ruta/patrón/parada" reutilizable por una app de edición: su unidad de trabajo es "una tabla GTFS = una tabla SQL casi idéntica al `.txt`", no un modelo normalizado por concepto de negocio.

**Entidades soportadas.** agency, stops, routes, trips, stop_times, shapes, calendar, calendar_dates, fare_attributes, fare_rules, frequencies, transfers, feed_info. Es decir, el **GTFS "clásico"** (pre Fares V2).

**Versión/estado.** `com.conveyal:gtfs-lib:7.1.0`, `pom.xml` fija **Java 8** como target. Dependencias clave: `javacsv` (parsing CSV propio, no un CSV parser moderno), MapDB 1.0.8 (almacenamiento intermedio en disco), JTS 1.16.1 (geometría), PostgreSQL JDBC 42.2.25, AWS SDK S3 (para persistir feeds en S3), GraphQL Java 11 (API de consulta interna de Data Tools).

**Limitaciones para GTFS moderno.**
- No hay ninguna referencia a Fares V2 (`fare_products`, `fare_leg_rules`, `fare_transfer_rules`, `rider_categories`, `fare_media`, `networks`, `areas`, `timeframes`, etc.) en README ni en el modelo de tablas.
- Target Java 8: corre en JVM 21 sin problema (bytecode hacia atrás es compatible), pero es una señal de que el proyecto no se está modernizando activamente al ritmo del spec.
- `javacsv` es un parser CSV antiguo y menos robusto en RFC4180 (comillas, saltos de línea embebidos) que alternativas modernas (Apache Commons CSV, `fastcsv`).
- El modelo de persistencia (MapDB + tablas casi 1:1 con los `.txt`) no es un modelo de dominio editable: no distingue "patrón de ruta" de "trip", no tiene concepto de versión de feed inmutable, no tiene IDs internos estables separados de los `*_id` de GTFS.
- Pensado para procesar/validar feeds ya existentes (import batch), no para servir como backend transaccional de un editor interactivo con undo/redo y auto-guardado.

**Partes reutilizables (conceptualmente, no como dependencia binaria).**
- El catálogo de campos/tablas y sus tipos, como referencia cruzada al construir nuestro propio mapeo.
- Las heurísticas de validación semántica clásica (fechas de calendar, referencias huérfanas, `shape_dist_traveled` decreciente) como checklist para nuestras `LOCAL QUALITY RULE`.

**Riesgos de usarlo como dependencia directa.**
1. Arrastrar Java 8 / `javacsv` / MapDB a un stack Spring Boot 3 / Java 21 moderno: fricción de compatibilidad y una segunda forma de persistencia (ficheros MapDB) que no necesitamos.
2. Su modelo de tablas no cubre Fares V2 → tendríamos que construir esa capa igual, y ahora con dos convenciones de modelo (la de gtfs-lib para lo clásico, la nuestra para Fares V2), duplicando lógica.
3. Es una dependencia pesada (AWS SDK, GraphQL) para algo que solo necesitamos parcialmente.

**Decisión (ver sección 52 del prompt).** `gtfs-lib` **no se incluye como dependencia Maven del backend**. Se documenta como referencia de diseño. Construimos nuestro propio módulo `gtfs-io` (lectura/escritura CSV + mapeo a nuestro dominio) detrás de la interfaz `GtfsEngine`, de forma que si en el futuro conviene usar `gtfs-lib` como *adaptador de importación alterno* para feeds legados grandes, se pueda enchufar sin tocar el dominio. Esto también resuelve el mandato del punto 63: la especificación vigente manda, no la cobertura histórica de gtfs-lib.

---

## B. Gap analysis GTFS Schedule (spec vigente, `gtfs.org/schedule/reference`)

| Archivo GTFS | Obligatoriedad | ¿`gtfs-lib` lo cubre? | Nuestra plataforma | Fase |
|---|---|---|---|---|
| `agency.txt` | Requerido | Sí | Modelo propio + exporter | 1 |
| `stops.txt` | Cond. requerido | Sí | Modelo propio + editor en mapa | 1 |
| `routes.txt` | Requerido | Sí | Modelo propio + editor | 1 |
| `trips.txt` | Requerido | Sí | Generado desde Pattern+Schedule | 1 |
| `stop_times.txt` | Requerido | Sí | Generado (3 métodos de tiempos) | 1 |
| `calendar.txt` | Cond. requerido | Sí | Editor de calendario semanal | 1 |
| `calendar_dates.txt` | Cond. requerido | Sí | Editor de excepciones | 1 |
| `shapes.txt` | Opcional | Sí | Generado desde dibujo en mapa | 1 |
| `frequencies.txt` | Opcional | Sí | Editor de frecuencias | 1 |
| `feed_info.txt` | Cond. requerido | Sí | Formulario dedicado | 1 |
| `fare_attributes.txt` / `fare_rules.txt` (Fares V1) | Opcional (legado) | Sí | Solo en import/export de compatibilidad | 2 |
| `fare_products.txt` | Opcional (Fares V2) | **No** | Formulario simplificado "Tarifa" → genera esto | 1 (caso simple) / 2 (avanzado) |
| `rider_categories.txt` | Opcional (Fares V2) | **No** | Perfiles tarifarios (estudiante, INAPAM, etc.) | 1 (caso simple) |
| `fare_media.txt` | Opcional (Fares V2) | **No** | Medios de pago (efectivo, tarjeta, QR) | 1 (caso simple) |
| `fare_leg_rules.txt` | Opcional (Fares V2) | **No** | Reglas por tramo | 2 |
| `fare_leg_join_rules.txt` | Opcional (Fares V2) | **No** | — | 2 |
| `fare_transfer_rules.txt` | Opcional (Fares V2) | **No** | Editor de transbordos | 2 |
| `timeframes.txt` | Opcional (Fares V2) | **No** | — | 2 |
| `networks.txt` / `route_networks.txt` | Opcional (Fares V2) | **No** | — | 2 |
| `areas.txt` / `stop_areas.txt` | Opcional (Fares V2) | **No** | — | 2 |
| `transfers.txt` | Opcional | Sí | Editor de transbordos físicos | 2 |
| `pathways.txt` / `levels.txt` | Opcional | Parcial | — | 3 |
| `translations.txt` | Opcional | No | — | 3 |
| `attributions.txt` | Opcional | No | — | 3 |
| `locations.geojson` / `location_groups.txt` / `location_group_stops.txt` | Opcional (demand-responsive) | No | — | 3 (fuera de alcance: bus urbano) |
| `booking_rules.txt` | Opcional (demand-responsive) | No | — | Fuera de alcance MVP |

Confirmado contra el reference vigente: Fares V2 ya es parte del **spec principal** (no una extensión aparte), por eso lo tratamos como ciudadano de primera clase desde el modelo de datos, aunque el formulario de captura empiece simple (sección 21).

---

## C. Arquitectura propuesta

```text
┌─────────────────────────────────────────────────────────────────┐
│  Frontend (React + TS + Vite)                                   │
│  MapLibre GL JS · TanStack Query · Zustand                      │
│  Editor cartográfico · Formularios · Tabla · Wizard · Validación│
└───────────────────────────┬───────────────────────────────────┘
                             │ REST/JSON (OpenAPI) — /api/v1
┌───────────────────────────▼───────────────────────────────────┐
│  Backend (Spring Boot 3 / Java 21)                               │
│  ┌───────────────┐ ┌───────────────┐ ┌───────────────────────┐ │
│  │ Domain services│ │ GtfsEngine    │ │ GtfsValidationService │ │
│  │ (Agency, Stop, │ │ (import/      │ │ → MobilityData        │ │
│  │  Route, Pattern│ │  export, capa │ │   validator (proceso  │ │
│  │  Calendar,     │ │  propia, sin  │ │   externo, jar)        │ │
│  │  Fare, ...)    │ │  gtfs-lib)    │ │ + reglas internas       │ │
│  └───────┬───────┘ └───────┬───────┘ └───────────┬─────────────┘ │
│          │                 │                     │               │
│  ┌───────▼─────────────────▼─────────────────────▼─────────┐   │
│  │ Repositorios JPA/Hibernate + PostGIS                     │   │
│  └───────────────────────────┬───────────────────────────────┘  │
│  RoutingProvider · MapTileProvider · GeocodingProvider (SPI)    │
└───────────────────────────┬───────────────────────────────────┘
                             │ JDBC
                    ┌────────▼────────┐
                    │ PostgreSQL 16 +  │
                    │ PostGIS 3.4       │
                    └────────────────┘
```

Principio (sección 56): el frontend nunca escribe GTFS. Todo pasa por el modelo de dominio → `GtfsEngine.exportFeed()` → ZIP.

---

## D. Modelo de datos (ERD inicial — Fase 1)

Entidades núcleo, todas con PK `UUID id` interno (nunca sale al GTFS) + columna `gtfs_id` (business key estable, sección 32):

```text
feed ──< feed_version ──< agency
                       ──< stop
                       ──< route ──< route_pattern ──< pattern_stop >── stop
                                                    ──< shape ──< shape_point
                       ──< service_calendar ──< service_exception
                       ──< trip (route_pattern, service_calendar) ──< stop_time >── stop
                       ──< frequency_entry (trip)
                       ──< fare_product ──< fare_leg_rule
                       ──< rider_category
                       ──< fare_media
                       ──< transfer_rule
                       ──< validation_run ──< validation_notice
```

Notas clave:
- `route_pattern` es la abstracción "IDA/REGRESO" del punto 8: agrupa un `shape` + una secuencia ordenada de `pattern_stop`. No es una tabla GTFS; de aquí se derivan `trips.txt`/`stop_times.txt`/`shapes.txt`.
- `trip` referencia `route_pattern` (de dónde saca `shape_id`, paradas y orden) + `service_calendar` (de dónde saca `service_id`). Los tiempos por parada de un trip concreto viven en `stop_time`, generados por los 3 métodos de la sección 16 pero siempre editables fila a fila.
- Geometría: `stop.geom POINT`, `route_pattern.geom LINESTRING`, `shape_point` guarda además `shape_pt_sequence`/`shape_dist_traveled` para exportar `shapes.txt` tal cual. Índices GiST en las tres.
- `fare_product`/`rider_category`/`fare_media`/`fare_leg_rule`/`fare_transfer_rule` mapean 1:1 a Fares V2; el formulario simple de la sección 21 escribe a estas tablas ocultando la complejidad, no crea un esquema paralelo.
- `validation_run`/`validation_notice` guardan cada corrida del validador interno + MobilityData validator, clasificadas `ERROR|WARNING|INFO` y etiquetadas `GTFS_SPEC|GTFS_BEST_PRACTICE|LOCAL_QUALITY_RULE` (sección 41).

El DDL completo vive en `database/migrations/` (Flyway) y se amplía en cada milestone.

---

## E. Flujo de generación GTFS (PostgreSQL → `gtfs.zip`)

```text
1. Usuario pulsa "Generar GTFS" sobre un feed_version en estado DRAFT
2. GtfsExportService abre una transacción de solo lectura
3. Por cada tabla GTFS: query paginada/streaming a PostGIS → mapea entidad → fila CSV
   (agency, stops, routes, trips, stop_times, calendar, calendar_dates,
    shapes, frequencies, feed_info, fare_*, transfers)
4. Cada writer usa Apache Commons CSV, RFC4180, UTF-8, sin BOM, línea CRLF según spec
5. Los .txt se escriben directamente al ZIP (sin subcarpetas), streaming a disco temporal
6. GtfsValidationService.validate(zip):
     a) reglas internas (rápidas, sección 24/41/42)
     b) proceso externo: java -jar gtfs-validator-cli.jar -i zip -o outDir
     c) parseo de report.json → validation_notice (uno por notice)
7. Política de publicación (sección 26): si hay ERROR bloqueante, el feed_version
   permanece DRAFT/INVALID; si solo hay WARNING, puede marcarse PUBLISHED con
   confirmación explícita del usuario
8. ZIP final + checksum SHA-256 + metadatos se guardan como artefacto inmutable
   ligado a ese feed_version (no se regenera en frío; se reusa hasta el próximo cambio)
```

Los `*_id` de salida son el valor de `gtfs_id` de cada entidad (nunca UUID, nunca un índice recalculado en cada export).

---

## F. Arquitectura cartográfica (OSM + MapLibre + proveedores)

```text
Frontend                          Backend (SPI)
┌──────────────┐   tiles raster   ┌───────────────────┐
│ MapLibre GL   │◄─────────────────│ MapTileProvider    │→ OSM standard (dev) /
│ (mapa base)   │   vía backend    │  (interfaz)        │  MapTiler/Stadia (prod)
├──────────────┤                  ├───────────────────┤
│ Editor:       │  POST waypoints  │ RoutingProvider     │→ Manual (sin red, Fase 1)
│ paradas,      │─────────────────►│  (interfaz)         │  OSRM/Valhalla (opt-in)
│ shapes,       │  ◄── geometría   │                     │
│ patrones      │  (nunca auto-    ├───────────────────┤
│               │   aceptada)      │ GeocodingProvider   │→ deshabilitado por defecto
└──────────────┘                  │  (interfaz)         │  (Nominatim propio si se
                                   └───────────────────┘   configura, nunca público)
```

Decisiones:
- **Tiles**: en dev, tiles raster estándar `tile.openstreetmap.org` con atribución `© OpenStreetMap contributors` visible siempre en el mapa (uso ligero, sin precarga masiva, cumple política de uso aceptable de OSM). En producción, `MapTileProvider` se reconfigura vía variable de entorno hacia un proveedor comercial o tiles propios — **no se cablea ningún proveedor en el código**.
- **Routing**: `RoutingProvider` con `OsrmRoutingProvider` activo por defecto (Modo 1/3 de la sección 9 — unir paradas existentes y rutear automáticamente por la red vial, igual que Conveyal construye patterns con su propio motor R5: `{r5url}/plan?...&mode=CAR`, código rastreado en `datatools-ui/lib/editor/util/map.js#route`). Probado con 15 pares de puntos reales en Hermosillo contra el demo público de OSRM (`router.project-osrm.org`): 15/15 éxito, ~0.9s promedio — mismo criterio que geocoding, no se hospeda un OSRM propio por defecto (requeriría descargar un extracto OSM regional), el demo público sirve para arrancar y `GTFSPLATFORM_OSRM_URL` se reconfigura hacia una instancia propia en producción. `ManualRoutingProvider` (líneas rectas, sin red vial) sigue disponible por config. Expuesto vía `POST /api/v1/routing/route`; el frontend llama a este endpoint automáticamente al ir seleccionando paradas existentes en la herramienta "📍 Agregar paradas" y muestra la geometría como propuesta editable (nunca auto-aceptada, sección 55) hasta que el usuario confirma "Guardar paradas y recorrido".

  **Criterio de ruteo.** El perfil `driving` del demo público de OSRM optimiza por `weight_name: "routability"` (velocidad permitida + tipo de vía, prioriza avenidas/vías principales) — **no** es distancia mínima. Confirmado inspeccionando la respuesta real de la API (no supuesto). Es el mismo criterio que usa el motor R5 de Conveyal con `mode=CAR` (tampoco es distancia mínima). Decisión confirmada con el usuario: se mantiene "más rápida/practicable" en vez de "más corta", porque para un camión de transporte público interesa que siga avenidas y vialidades reales, no el callejón técnicamente más corto. El demo público de OSRM no expone un perfil de distancia pura vía API — para eso haría falta un OSRM propio con un perfil custom.
- **Geocoding**: `GeocodingProvider.suggestStopName(lat, lon)` — sugiere nombre de parada por intersección más cercana ("Calle Rosales & Avenida Doctor Paliza"), igual que Conveyal Data Tools. A diferencia del autocomplete indiscriminado que sí evitamos (sección 4), esto es **una sola consulta puntual** disparada por una acción explícita del usuario (crear una parada), no un typeahead continuo — por eso sí tiene una implementación activa por defecto. Dos implementaciones, seleccionables por config, sin cambiar el resto de la app:
  - `EsriIntersectionGeocodingProvider` (por defecto): replica el mecanismo real que usa el editor de Conveyal — se rastreó el código fuente de `datatools-ui` (`lib/editor/util/map.js#constructStop` + `lib/scenario-editor/utils/reverse.js#reverseEsri`) y usa exactamente el mismo endpoint: `ArcGIS World Geocoding Service` (`geocode.arcgis.com/.../reverseGeocode`) con `returnIntersection=true`, que devuelve el string `"Calle A & Avenida B"` ya formateado por Esri. Más confiable (servicio comercial con SLA) a cambio de depender de un tercero propietario; no requiere API key para uso básico no persistido (20,000 consultas/mes gratis), con un token opcional (`gtfsplatform.geocoding.esri-api-key`) para más cuota en producción.
  - `OverpassGeocodingProvider`: calcula la intersección nosotros mismos a partir de geometría cruda de vías OSM vía la API pública de Overpass, rotando entre varios espejos comunitarios. 100% datos abiertos, pero mostró conectividad intermitente en pruebas.

  **Prueba comparativa con 100 puntos aleatorios reales en Hermosillo** (script ad-hoc, no forma parte del test suite del repo):

  | | Overpass | Esri |
  |---|---|---|
  | Con sugerencia | 0/32 (se cortó ahí — 0% de éxito) | 76/100 (76%) |
  | Errores de red/timeout | 32/32 | 0/100 |
  | Latencia promedio | ~19 s | 0.53 s |
  | Latencia máxima | ~23 s | 1.21 s |

  El 24% restante de Esri son respuestas 200 legítimas sin intersección cercana (puntos en el borde del área de prueba), no errores. Por esto el default cambió de `overpass` a `esri` — Overpass queda disponible como alternativa 100% open-data para quien prefiera esa garantía sobre la velocidad/confiabilidad.

  Ambas se degradan en silencio a "sin sugerencia" ante cualquier fallo — nunca bloquean el alta de la parada, y el nombre sugerido siempre queda editable. En producción, `gtfsplatform.geocoding.overpass-url` también se puede reconfigurar hacia una instancia propia — mismo patrón que `RoutingProvider`/OSRM.

---

## G. Estructura del repositorio

```text
GTFS-Platform/
├── backend/
│   ├── src/main/java/mx/gtfsplatform/...
│   ├── src/main/resources/
│   │   └── validator/gtfs-validator-8.0.1-cli.jar   (descargado en build)
│   ├── src/test/java/...
│   ├── pom.xml
│   └── Dockerfile
├── frontend/
│   ├── src/
│   │   ├── map/            (MapLibre + editor cartográfico)
│   │   ├── features/        (stops, routes, patterns, calendars, fares, validation)
│   │   ├── api/              (cliente generado desde OpenAPI)
│   │   └── store/            (Zustand)
│   ├── package.json
│   ├── vite.config.ts
│   └── Dockerfile
├── database/
│   └── migrations/           (Flyway V1__..., V2__...)
├── docs/
│   ├── ARCHITECTURE-PLAN.md  (este documento)
│   ├── ARCHITECTURE.md
│   ├── GTFS-MAPPING.md
│   ├── API.md
│   └── DEPLOYMENT.md
├── scripts/                   (seed demo, utilidades dev)
├── docker-compose.yml
├── .env.example
├── README.md
└── CONTRIBUTING.md
```

---

## H. Roadmap desglosado

**Fase 1 (MVP — en curso ahora):**
1. Monorepo, docker-compose (Postgres+PostGIS), Flyway baseline.
2. Backend: entidades núcleo + repos + REST `/api/v1` (agency, feed, stop, route, pattern, calendar, fare simple, feed_info).
3. `GtfsEngine`: exporter (dominio → CSV → ZIP) y import básico (ZIP → dominio).
4. `GtfsValidationService`: reglas internas + integración `gtfs-validator` CLI.
5. Frontend: mapa MapLibre, alta de parada por clic, panel lateral, buscador de cercanas.
6. Frontend: alta de ruta, patrón IDA/REGRESO, dibujo manual de shape (vértices, undo/redo), asociación/orden de paradas.
7. Frontend: calendario semanal + excepciones, horario por frecuencia y explícito (métodos A/B/C de tiempos).
8. Frontend: tarifa simple (Fares V2 oculto tras formulario simple).
9. Panel de validación en vivo + reporte del validador oficial navegable.
10. Exportar `gtfs.zip`, importar de vuelta, reconstrucción del modelo.
11. Seed demo IMTES/Ruta 18 (Hermosillo, datos de ejemplo).
12. Tests unitarios/integración clave + prueba E2E del camino crítico.

**Fase 2:** Fares V2 avanzado, `transfers.txt`, import GIS (GeoJSON/KML/GPX/CSV/Shapefile), comparador de versiones, edición masiva, dashboard avanzado.

**Fase 3:** usuarios/organizaciones/roles finos, workflows de aprobación, publicación automática, API pública, analítica, GTFS Realtime.

---

## I. Riesgos técnicos y mitigación

| Riesgo | Mitigación |
|---|---|
| `gtfs-validator` requiere JVM propia dentro del contenedor backend (proceso externo) | Empaquetar el jar en la imagen Docker del backend; invocar vía `ProcessBuilder` con timeout y límites de memoria (`-Xmx`) |
| Homebrew roto en este macOS (beta no reconocida) impide instalar Java 21/Maven/Node localmente | Todo el build/test corre dentro de Docker (multi-stage); no se depende del toolchain del host |
| `shape_dist_traveled` y proximidad parada-shape requieren distancia geodésica correcta | Usar PostGIS (`ST_Length`, `ST_LineLocatePoint` sobre geografía) en vez de matemática manual en el backend |
| Horarios >24:00:00 | Modelar tiempos como segundos-desde-medianoche del `service day` (entero), nunca `java.time.LocalTime`; solo formatear a `HH:MM:SS` (permitiendo horas ≥24) al exportar |
| IDs regenerados en cada export rompiendo continuidad entre versiones | `gtfs_id` se asigna una vez al crear la entidad y se copia al clonar/versionar; el exporter nunca genera un id nuevo si la entidad ya tiene uno |
| Validador oficial tarda (feeds grandes) y bloquea el hilo HTTP | Ejecutar como job asíncrono (`validation_run` con estado `RUNNING`→`DONE`), el frontend hace polling/consulta de estado |
| Concurrencia: dos usuarios editando el mismo feed_version | Bloqueo optimista (columna `version`/`updated_at` + chequeo en el `UPDATE`), auto-guardado con reintento |
| Volumen (millones de `stop_time`) satura el frontend | Paginación server-side, consultas espaciales acotadas al viewport del mapa, nunca `SELECT *` de `stop_times` completo |
| Zip Slip / path traversal en import | Validar cada `ZipEntry.getName()` contra path traversal antes de extraer; whitelist de nombres de archivo GTFS conocidos |

---

## J. Decisiones que requieren input del usuario

1. ~~Proveedor de routing real~~ — resuelto: `OsrmRoutingProvider` activo por defecto contra el demo público, ver sección F.
2. **Proveedor de tiles para producción.** Dev usa tiles OSM estándar con atribución. Para producción real se necesita una cuenta en un proveedor (MapTiler/Stadia/Maptiler self-hosted) o tiles propios — puedo dejarlo parametrizado por variable de entorno sin necesidad de decidirlo ahora.
3. ~~Autenticación real~~ — resuelto parcialmente: registro/login propios (email + contraseña, JWT firmado con HMAC, `BCryptPasswordEncoder`) reemplazan el acceso abierto que tenía el MVP. Cada usuario administra solo los feeds que creó (`feed.created_by`), un rol ADMIN ve todos. La ownership se aplica a nivel de `Feed` (list/get/update/delete); los recursos anidados (paradas, rutas, patrones, etc.) solo exigen sesión válida, no verifican dueño del feed padre — aceptable para el alcance actual (UUIDs no adivinables, sin usuarios hostiles), pero es la brecha a cerrar si esto pasa a multi-tenant real. Sigue pendiente conectar un IdP externo (OAuth2/OIDC/Keycloak/Entra ID, sección 50) si la organización lo requiere más adelante.
4. **Para producción real, tanto Overpass/OSRM públicos como Esri deberían pasar a instancias propias/con cuota garantizada** — los defaults actuales (probados con datos reales) son idóneos para desarrollo y demos, no para carga sostenida de una operación en producción.

Ninguna bloquea el criterio de aceptación del MVP (sección 61 del prompt); quedan resueltas con valores por defecto razonables y probados, e interfaces ya desacopladas para cambiarlas después.

---

*Siguiente paso: inicializar el monorepo y comenzar la Fase 1 según el roadmap de la sección H, sin esperar confirmación adicional, según lo indicado.*
