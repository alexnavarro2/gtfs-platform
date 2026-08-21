# GTFS Platform

Plataforma web para crear, editar, validar y exportar feeds **GTFS Schedule** desde una
interfaz gráfica sobre OpenStreetMap. Ver el análisis completo en
[`docs/ARCHITECTURE-PLAN.md`](docs/ARCHITECTURE-PLAN.md).

## Arrancar el proyecto

Requiere Docker (con el daemon corriendo). No hace falta Java, Maven ni Node instalados
en el host — todo el build ocurre dentro de contenedores.

```bash
cp .env.example .env
docker compose up --build
```

- Frontend: http://localhost:5173
- Backend / API: http://localhost:8080/api/v1
- Swagger UI: http://localhost:8080/api/v1/docs

Si el puerto 5432 ya está en uso en tu máquina por otro Postgres, ajusta
`POSTGRES_PORT` en `.env` (por defecto usa `5433` en el host para no chocar).

## Primer uso

1. Abre http://localhost:5173 — te pedirá crear un **feed** (ej. "IMTES Demo") y luego
   la **agencia** (nombre, sitio web, zona horaria).
2. Pestaña **Paradas** → "+ Agregar parada" → clic en el mapa → nombre → Guardar.
3. Pestaña **Rutas** → crear ruta → seleccionarla → "+ Nuevo sentido" (IDA/REGRESO).
4. Con el sentido seleccionado: "✏️ Dibujar" para trazar el recorrido a mano sobre el
   mapa, "📍 Agregar paradas" para asociarlas en orden.
5. Pestaña **Calendarios** → crear un servicio (días + fechas).
6. En el editor del sentido, sección **Horario** → frecuencia o explícito → generar
   trips y stop_times.
7. Pestaña **Validación** → "Generar GTFS" → "Completa (MobilityData)" → 0 errores →
   "⬇ Descargar gtfs.zip".

## Estructura

Ver `docs/ARCHITECTURE-PLAN.md` sección G para el árbol completo del monorepo.

```text
backend/     Spring Boot 3 / Java 21 — dominio, exportador/importador GTFS, validación
frontend/    React + TypeScript + Vite + MapLibre GL JS
database/    Migraciones Flyway (fuente de verdad del esquema)
docs/        Documentación (arquitectura, mapeo GTFS)
```

## Estado (Fase 1 — MVP)

Implementado y verificado extremo a extremo vía API (creación de feed → agencia →
paradas → ruta → patrón/sentido → shape dibujado a mano → paradas ordenadas →
calendario → horario por frecuencia → generación de `trips`/`stop_times`/`shapes` →
exportación a `gtfs.zip` → validación interna → **validación oficial con el
MobilityData Canonical GTFS Validator (0 errores)** → descarga → reimportación con
reconstrucción correcta del modelo).

Pendiente de verificación visual por el usuario: el editor cartográfico (MapLibre)
compila y su lógica fue revisada, pero no pude confirmarlo con una captura de pantalla
real — los navegadores automatizados disponibles en esta sesión mantienen la pestaña
en `document.hidden = true`, lo que suspende el bucle de animación (`requestAnimationFrame`)
del que depende MapLibre para pintar tiles. Es una limitación de las herramientas de
esta sesión, no del código. Abre http://localhost:5173 en tu navegador normal para
confirmarlo.

Pendiente para fases siguientes (ver sección H del plan de arquitectura): Fares V2
avanzado, `transfers.txt` completo, importación GIS (GeoJSON/KML/GPX/Shapefile),
comparador de versiones, edición masiva, `RoutingProvider` real (OSRM), usuarios/roles
con IdP externo.
