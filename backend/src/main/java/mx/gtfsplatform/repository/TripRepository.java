package mx.gtfsplatform.repository;

import mx.gtfsplatform.domain.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {
    List<Trip> findByRoutePatternId(UUID routePatternId);
    List<Trip> findByRoutePatternIdIn(List<UUID> routePatternIds);
    List<Trip> findByServiceCalendarId(UUID serviceCalendarId);
    void deleteByRoutePatternId(UUID routePatternId);
}
