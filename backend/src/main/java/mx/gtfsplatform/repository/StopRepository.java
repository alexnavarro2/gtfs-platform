package mx.gtfsplatform.repository;

import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.Stop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StopRepository extends JpaRepository<Stop, UUID> {

    List<Stop> findByFeedVersionId(UUID feedVersionId);

    List<Stop> findByParentStationId(UUID parentStationId);

    @Query(value = "SELECT * FROM stop s WHERE ST_DWithin(s.geom::geography, "
            + "ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :radiusMeters)", nativeQuery = true)
    List<Stop> findNear(@Param("lat") double lat, @Param("lon") double lon, @Param("radiusMeters") double radiusMeters);
}
