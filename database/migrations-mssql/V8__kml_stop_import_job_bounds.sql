-- Para que el mapa pueda centrarse solo en las paradas recién importadas
-- (en vez de que el usuario tenga que buscarlas a mano), el worker va
-- llevando la caja delimitadora (bounding box) de los puntos procesados.
ALTER TABLE kml_stop_import_job ADD min_lat FLOAT;
ALTER TABLE kml_stop_import_job ADD max_lat FLOAT;
ALTER TABLE kml_stop_import_job ADD min_lon FLOAT;
ALTER TABLE kml_stop_import_job ADD max_lon FLOAT;
