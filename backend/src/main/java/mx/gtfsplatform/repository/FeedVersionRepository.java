package mx.gtfsplatform.repository;

import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.FeedVersion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedVersionRepository extends JpaRepository<FeedVersion, UUID> {
    List<FeedVersion> findByFeedId(UUID feedId);
}
