package mx.gtfsplatform.repository;

import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.PatternStop;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatternStopRepository extends JpaRepository<PatternStop, UUID> {
    List<PatternStop> findByRoutePatternIdOrderByStopSequenceAsc(UUID routePatternId);

    List<PatternStop> findByStopId(UUID stopId);

    void deleteByRoutePatternId(UUID routePatternId);
}
