# Mapeo entidad interna → GTFS

| Entidad interna (PostgreSQL) | Archivo GTFS | Notas |
|---|---|---|
| `agency` | `agency.txt` | 1:1 |
| `stop` | `stops.txt` | `geom` (PostGIS Point) → `stop_lat`/`stop_lon` |
| `route` | `routes.txt` | 1:1 |
| `route_pattern` + `shape_point` | `shapes.txt` | No es un archivo GTFS por sí sola; `shape_gtfs_id` es el `shape_id` exportado. `shape_dist_traveled` se recalcula en cada export vía distancia geodésica acumulada (nunca se guarda un valor obsoleto) |
| `route_pattern` + `service_calendar` (uno por horario generado) | `trips.txt` | `direction_id`, `shape_id`, `trip_headsign` salen del patrón; `service_id` del calendario |
| `pattern_stop` + `stop_time` | `stop_times.txt` | Tiempos guardados como segundos-desde-medianoche (soporta >= 24:00:00) |
| `service_calendar` | `calendar.txt` | 1:1 |
| `service_exception` | `calendar_dates.txt` | 1:1 |
| `frequency_entry` (sobre un `trip` con `is_frequency_based=true`) | `frequencies.txt` | El trip "plantilla" define el patrón de tiempos relativo |
| `feed_version` (campos `feed_*`) | `feed_info.txt` | Solo se exporta si `feed_publisher_name` está definido |
| `rider_category` | `rider_categories.txt` | Fares V2 |
| `fare_media` | `fare_media.txt` | Fares V2 |
| `fare_product` | `fare_products.txt` | Fares V2; el formulario "Tarifa simple" de la UI escribe aquí directamente |
| `fare_leg_rule` | `fare_leg_rules.txt` | Fares V2; `network_id` nulo = aplica a toda la red |
| `fare_transfer_rule` | `fare_transfer_rules.txt` | Fares V2 |
| `transfer_rule` | `transfers.txt` | Transbordos físicos entre paradas |
| `validation_run` + `validation_notice` | *(no exportable)* | Auditoría de corridas de validación, interna + MobilityData |
| `export_artifact` | *(no exportable)* | Metadatos del `gtfs.zip` generado (sha256, tamaño, fecha) |

## Archivos GTFS opcionales aún no cubiertos (Fase 2/3)

`translations.txt`, `attributions.txt`, `pathways.txt`, `levels.txt`, `networks.txt`,
`route_networks.txt`, `areas.txt`, `stop_areas.txt`, `timeframes.txt`,
`fare_attributes.txt`/`fare_rules.txt` (Fares V1, solo para importación de
compatibilidad), `locations.geojson` y demás archivos de transporte a demanda (fuera
de alcance: la plataforma está orientada a bus urbano de horario fijo).

## Reglas de generación de IDs (persistencia entre versiones)

Cada entidad tiene una columna `gtfs_id` (o `shape_gtfs_id` en `route_pattern`) que se
asigna **una sola vez**, en el momento de creación (`GtfsIdGenerator`), y nunca se
recalcula al exportar. El exportador siempre usa el valor ya almacenado. Ver
`docs/ARCHITECTURE-PLAN.md` sección J / I para el detalle de la estrategia.
