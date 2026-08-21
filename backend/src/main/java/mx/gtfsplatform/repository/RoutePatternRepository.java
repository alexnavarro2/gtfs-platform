package mx.gtfsplatform.repository;

import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.RoutePattern;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutePatternRepository extends JpaRepository<RoutePattern, UUID> {
    List<RoutePattern> findByRouteId(UUID routeId);
}
