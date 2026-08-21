package mx.gtfsplatform.repository;

import java.util.UUID;
import mx.gtfsplatform.domain.Feed;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedRepository extends JpaRepository<Feed, UUID> {
}
