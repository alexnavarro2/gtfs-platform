package mx.gtfsplatform.repository;

import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.FareMedia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FareMediaRepository extends JpaRepository<FareMedia, UUID> {
    List<FareMedia> findByFeedVersionId(UUID feedVersionId);
}
