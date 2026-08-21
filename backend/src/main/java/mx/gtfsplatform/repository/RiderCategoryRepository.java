package mx.gtfsplatform.repository;

import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.RiderCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiderCategoryRepository extends JpaRepository<RiderCategory, UUID> {
    List<RiderCategory> findByFeedVersionId(UUID feedVersionId);
}
