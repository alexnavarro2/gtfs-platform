package mx.gtfsplatform.repository;

import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.Route;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<Route, UUID> {
    List<Route> findByFeedVersionId(UUID feedVersionId);

    List<Route> findByAgencyId(UUID agencyId);
}
