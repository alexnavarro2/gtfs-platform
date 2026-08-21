package mx.gtfsplatform.repository;

import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.ServiceException;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceExceptionRepository extends JpaRepository<ServiceException, UUID> {
    List<ServiceException> findByServiceCalendarId(UUID serviceCalendarId);
}
