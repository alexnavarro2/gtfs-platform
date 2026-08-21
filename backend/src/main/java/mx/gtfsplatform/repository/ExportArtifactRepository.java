package mx.gtfsplatform.repository;

import mx.gtfsplatform.domain.ExportArtifact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExportArtifactRepository extends JpaRepository<ExportArtifact, UUID> {
    List<ExportArtifact> findByFeedVersionIdOrderByGeneratedAtDesc(UUID feedVersionId);
}
