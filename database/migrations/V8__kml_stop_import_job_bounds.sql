-- Para que el mapa pueda centrarse solo en las paradas recién importadas
-- (en vez de que el usuario tenga que buscarlas a mano), el worker va
-- llevando la caja delimitadora (bounding box) de los puntos procesados.
ALTER TABLE kml_stop_import_job ADD COLUMN min_lat DOUBLE PRECISION;
ALTER TABLE kml_stop_import_job ADD COLUMN max_lat DOUBLE PRECISION;
ALTER TABLE kml_stop_import_job ADD COLUMN min_lon DOUBLE PRECISION;
ALTER TABLE kml_stop_import_job ADD COLUMN max_lon DOUBLE PRECISION;
