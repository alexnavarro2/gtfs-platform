package mx.gtfsplatform.repository;

import mx.gtfsplatform.domain.ImportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {
    List<ImportJob> findByFeedIdOrderByStartedAtDesc(UUID feedId);
}
