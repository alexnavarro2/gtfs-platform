package mx.gtfsplatform.repository;

import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.ShapePoint;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShapePointRepository extends JpaRepository<ShapePoint, UUID> {
    List<ShapePoint> findByRoutePatternIdOrderByShapePtSequenceAsc(UUID routePatternId);

    void deleteByRoutePatternId(UUID routePatternId);
}
