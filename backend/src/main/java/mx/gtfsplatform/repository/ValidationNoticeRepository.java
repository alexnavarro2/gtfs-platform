package mx.gtfsplatform.repository;

import mx.gtfsplatform.domain.ValidationNotice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ValidationNoticeRepository extends JpaRepository<ValidationNotice, UUID> {
    List<ValidationNotice> findByValidationRunId(UUID validationRunId);
}
