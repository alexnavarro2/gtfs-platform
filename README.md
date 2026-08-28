# GTFS Platform

Plataforma web para crear, editar, validar y exportar feeds **GTFS Schedule** desde una
interfaz gráfica sobre OpenStreetMap — paradas, rutas, sentidos/patrones, horarios,
tarifas (Fares V2), importación desde KML, y exportación/validación GTFS oficial
(MobilityData Canonical GTFS Validator).

## Stack

| | |
|---|---|
| Backend | Java 21 · Spring Boot 3 · Hibernate/JPA · Flyway |
| Frontend | React 18 + TypeScript + Vite — **sin framework encima** (no Next.js/Remix), estado con Zustand, mapa con MapLibre GL JS |
| Base de datos | PostgreSQL + PostGIS (default) **o** SQL Server (soportado igual, ver abajo) — se elige por variables de entorno, sin recompilar |

Un solo repositorio, dos aplicaciones independientes (`backend/`, `frontend/`) más la
base de datos — se construyen y despliegan por separado.

## Correr el proyecto en local (Docker)

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

## Desplegarlo (dos caminos, según dónde vaya a vivir)

Este repo sirve **dos escenarios de despliegue** distintos — de ahí que haya más de un
`docker-compose*.yml` y más de un `.env.*.example` en la raíz; cada combo es para un
caso:

| Escenario | Archivos que le tocan | Guía |
|---|---|---|
| VPS propio con Docker (Postgres + Caddy + HTTPS automático) | `docker-compose.prod.yml`, `Caddyfile`, `.env.production.example` | [`DEPLOY.md`](DEPLOY.md) |
| Servidor Windows sin Docker + SQL Server + IIS (caso IMTES) | `database/migrations-mssql/`, `.env.mssql.example` (variables del backend) | pendiente de `web.config`/Windows Service — ver sección de abajo |

`docker-compose.yml` (sin sufijo) es solo para desarrollo local, no para producción.

### Base de datos: Postgres o SQL Server, misma app

El backend soporta ambos motores desde el mismo `.jar`, sin recompilar — la
diferencia son 3 variables de entorno (`SPRING_DATASOURCE_URL`,
`GTFSPLATFORM_HIBERNATE_DIALECT`, `GTFSPLATFORM_FLYWAY_LOCATIONS`). Ver el
comentario correspondiente en [`.env.example`](.env.example) para el detalle, y
[`.env.mssql.example`](.env.mssql.example) para los valores concretos del caso
SQL Server. `database/migrations-mssql/` trae el mismo esquema que
`database/migrations/` reescrito en T-SQL — probado contra un SQL Server 2022 real
(no solo revisado a mano): las 8 migraciones aplican limpio y un flujo completo por
la API (feed → agencia → paradas → ruta → sentido → trazo → export GTFS → borrado en
cascada) funciona de punta a punta.

### Pendiente para el despliegue en el servidor de IMTES

No es código — son datos concretos de su servidor que hacen falta para terminar la
guía: versión de SQL Server y método de autenticación, si IIS ya tiene ARR + URL
Rewrite instalado, si Java 21 ya está en el servidor, y el nombre exacto de la ruta
pública (ej. `imtes.sonora.gob.mx/gtfs`).

## Primer uso (una vez levantado)

1. Abre la URL del frontend — te pedirá crear un **feed** (ej. "IMTES Demo") y luego
   la **agencia** (nombre, sitio web, zona horaria).
2. Pestaña **Paradas** → crear a mano o importar un KML (geocodifica cada punto por
   la intersección más cercana automáticamente).
3. Pestaña **Rutas** → crear ruta (o importar varias de un jalón desde un KML con
   una línea por ruta) → seleccionarla → "+ Nuevo sentido" (IDA/REGRESO).
4. Con el sentido seleccionado: "✏️ Dibujar" a mano sobre el mapa, "📍 Agregar
   paradas" para unirlas con ruteo automático por calles, o importar el trazo de un
   KML (se pega solo a la calle real).
5. Pestaña **Calendarios** → crear un servicio (días + fechas).
6. En el editor del sentido, sección **Horario** → frecuencia o explícito → generar
   trips y stop_times.
7. Pestaña **Tarifas** → categorías de pasajero, medios de pago, tarifas, reglas por
   tramo y de transbordo (GTFS Fares V2).
8. Pestaña **Validación** → "Generar GTFS" → "Completa (MobilityData)" → 0 errores →
   "⬇ Descargar gtfs.zip".

## Estructura del repo

```text
backend/                    Spring Boot 3 / Java 21 — dominio, exportador/importador
                             GTFS, validación, auth, KML, ruteo/geocoding
frontend/                   React + TypeScript + Vite + MapLibre GL JS (SPA, un solo
                             build estático — sin SSR ni framework encima)
database/migrations/        Esquema Postgres/PostGIS (Flyway) — dev local y VPS Docker
database/migrations-mssql/  El mismo esquema en T-SQL — despliegue en SQL Server
docs/                       Documentación de arquitectura y mapeo GTFS
docker-compose.yml          Stack de desarrollo local (Postgres)
docker-compose.prod.yml     Stack de producción en VPS propio (Postgres + Caddy/HTTPS)
Caddyfile                   Reverse proxy + HTTPS automático, usado por el compose de prod
DEPLOY.md                   Guía paso a paso: VPS gratuito (Oracle Cloud) + dominio propio
.env.example                Variables para desarrollo local
.env.production.example     Variables para el despliegue VPS Docker
.env.mssql.example          Variables del backend para el despliegue SQL Server/Windows
```

Ver [`docs/ARCHITECTURE-PLAN.md`](docs/ARCHITECTURE-PLAN.md) para el análisis
completo de arquitectura y el detalle de cada sección del producto.

## Estado

Implementado y verificado extremo a extremo (vía API real, no solo revisado a mano):
feed → agencia → paradas (a mano o KML) → rutas (a mano o KML, una o varias a la
vez) → patrón/sentido → recorrido (a mano, uniendo paradas con ruteo por calles, o
importado de KML) → calendario → horario (frecuencia o explícito) → Fares V2
completo (categorías, medios, tarifas, reglas por tramo y transbordo) → generación
de `trips`/`stop_times`/`shapes` → exportación a `gtfs.zip` → validación interna y
oficial (MobilityData Canonical GTFS Validator, 0 errores) → descarga →
reimportación con reconstrucción correcta del modelo → soporte de PostgreSQL y SQL
Server como motores intercambiables.

Pendiente: `transfers.txt` a nivel parada (existe el backend, sin UI — distinto de
las reglas de transbordo de Fares V2, que sí están completas), editar/eliminar
calendario y `calendar_dates.txt` (excepciones), publicar una versión
(`PUBLISHED`)/crear nueva versión de feed desde la UI (el backend ya lo soporta),
importación de un GTFS `.zip` completo desde la UI (el backend ya lo soporta).
