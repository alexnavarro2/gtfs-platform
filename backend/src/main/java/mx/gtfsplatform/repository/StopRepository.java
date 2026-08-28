package mx.gtfsplatform.repository;

import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.Stop;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StopRepository extends JpaRepository<Stop, UUID> {

    List<Stop> findByFeedVersionId(UUID feedVersionId);

    List<Stop> findByParentStationId(UUID parentStationId);

    // El "cerca de aquí" (aviso de posible duplicado al crear una parada, sección 6)
    // se resuelve en Java (StopController.near), no aquí — antes era un @Query nativo
    // con ST_DWithin/ST_MakePoint (PostGIS), que no es portable a SQL Server. Filtrar
    // en memoria sobre las paradas de un solo feed_version (cientos, no millones) es
    // barato y funciona igual en cualquier motor.
}
