package mx.gtfsplatform.repository;

import mx.gtfsplatform.domain.ValidationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ValidationRunRepository extends JpaRepository<ValidationRun, UUID> {
    List<ValidationRun> findByFeedVersionIdOrderByStartedAtDesc(UUID feedVersionId);
}
