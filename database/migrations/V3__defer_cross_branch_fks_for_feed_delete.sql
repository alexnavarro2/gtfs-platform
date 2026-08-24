-- Al borrar un feed, el DELETE en cascada desde feed_version toca varias
-- ramas hermanas (agency/stop/route/route_pattern/trip/pattern_stop/...) en
-- la misma transacción. Los FKs que cruzan esas ramas sin ON DELETE CASCADE
-- (p.ej. route.agency_id, pattern_stop.stop_id, stop.parent_station_id) se
-- validaban de forma inmediata, así que si Postgres borraba una fila padre
-- antes que su hija cruzada, la transacción entera fallaba con 23503 aunque
-- ambas iban a quedar borradas de todas formas. Se vuelven DEFERRABLE
-- INITIALLY DEFERRED: la validación ocurre al COMMIT, cuando todo el árbol
-- ya fue borrado, sin perder la protección para un DELETE aislado (que sigue
-- fallando al hacer commit si la fila referenciada sigue viva).

ALTER TABLE route
    DROP CONSTRAINT route_agency_id_fkey,
    ADD CONSTRAINT route_agency_id_fkey FOREIGN KEY (agency_id) REFERENCES agency(id)
        DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE stop
    DROP CONSTRAINT stop_parent_station_id_fkey,
    ADD CONSTRAINT stop_parent_station_id_fkey FOREIGN KEY (parent_station_id) REFERENCES stop(id)
        DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE pattern_stop
    DROP CONSTRAINT pattern_stop_stop_id_fkey,
    ADD CONSTRAINT pattern_stop_stop_id_fkey FOREIGN KEY (stop_id) REFERENCES stop(id)
        DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE stop_time
    DROP CONSTRAINT stop_time_pattern_stop_id_fkey,
    ADD CONSTRAINT stop_time_pattern_stop_id_fkey FOREIGN KEY (pattern_stop_id) REFERENCES pattern_stop(id)
        DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE trip
    DROP CONSTRAINT trip_service_calendar_id_fkey,
    ADD CONSTRAINT trip_service_calendar_id_fkey FOREIGN KEY (service_calendar_id) REFERENCES service_calendar(id)
        DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE transfer_rule
    DROP CONSTRAINT transfer_rule_from_stop_id_fkey,
    ADD CONSTRAINT transfer_rule_from_stop_id_fkey FOREIGN KEY (from_stop_id) REFERENCES stop(id)
        DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE transfer_rule
    DROP CONSTRAINT transfer_rule_to_stop_id_fkey,
    ADD CONSTRAINT transfer_rule_to_stop_id_fkey FOREIGN KEY (to_stop_id) REFERENCES stop(id)
        DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE fare_product
    DROP CONSTRAINT fare_product_rider_category_id_fkey,
    ADD CONSTRAINT fare_product_rider_category_id_fkey FOREIGN KEY (rider_category_id) REFERENCES rider_category(id)
        DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE fare_product
    DROP CONSTRAINT fare_product_fare_media_id_fkey,
    ADD CONSTRAINT fare_product_fare_media_id_fkey FOREIGN KEY (fare_media_id) REFERENCES fare_media(id)
        DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE fare_leg_rule
    DROP CONSTRAINT fare_leg_rule_fare_product_id_fkey,
    ADD CONSTRAINT fare_leg_rule_fare_product_id_fkey FOREIGN KEY (fare_product_id) REFERENCES fare_product(id)
        DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE fare_transfer_rule
    DROP CONSTRAINT fare_transfer_rule_fare_product_id_fkey,
    ADD CONSTRAINT fare_transfer_rule_fare_product_id_fkey FOREIGN KEY (fare_product_id) REFERENCES fare_product(id)
        DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE import_job
    DROP CONSTRAINT import_job_result_feed_version_id_fkey,
    ADD CONSTRAINT import_job_result_feed_version_id_fkey FOREIGN KEY (result_feed_version_id) REFERENCES feed_version(id)
        DEFERRABLE INITIALLY DEFERRED;
