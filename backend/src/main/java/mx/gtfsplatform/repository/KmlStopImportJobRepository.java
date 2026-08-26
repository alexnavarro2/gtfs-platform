package mx.gtfsplatform.repository;

import java.util.UUID;
import mx.gtfsplatform.domain.KmlStopImportJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KmlStopImportJobRepository extends JpaRepository<KmlStopImportJob, UUID> {
}
