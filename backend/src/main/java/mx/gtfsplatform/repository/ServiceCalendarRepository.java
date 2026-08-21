package mx.gtfsplatform.repository;

import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.ServiceCalendar;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceCalendarRepository extends JpaRepository<ServiceCalendar, UUID> {
    List<ServiceCalendar> findByFeedVersionId(UUID feedVersionId);
}
