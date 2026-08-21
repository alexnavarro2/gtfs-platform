package mx.gtfsplatform.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.FeedVersion;
import mx.gtfsplatform.domain.ServiceCalendar;
import mx.gtfsplatform.domain.ServiceException;
import mx.gtfsplatform.gtfs.GtfsIdGenerator;
import mx.gtfsplatform.repository.FeedVersionRepository;
import mx.gtfsplatform.repository.ServiceCalendarRepository;
import mx.gtfsplatform.repository.ServiceExceptionRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceCalendarController {

    private final ServiceCalendarRepository serviceCalendarRepository;
    private final ServiceExceptionRepository serviceExceptionRepository;
    private final FeedVersionRepository feedVersionRepository;
    private final GtfsIdGenerator idGenerator;

    public ServiceCalendarController(ServiceCalendarRepository serviceCalendarRepository,
            ServiceExceptionRepository serviceExceptionRepository, FeedVersionRepository feedVersionRepository,
            GtfsIdGenerator idGenerator) {
        this.serviceCalendarRepository = serviceCalendarRepository;
        this.serviceExceptionRepository = serviceExceptionRepository;
        this.feedVersionRepository = feedVersionRepository;
        this.idGenerator = idGenerator;
    }

    @GetMapping("/api/v1/feed-versions/{feedVersionId}/calendars")
    public List<ServiceCalendar> list(@PathVariable UUID feedVersionId) {
        return serviceCalendarRepository.findByFeedVersionId(feedVersionId);
    }

    @PostMapping("/api/v1/feed-versions/{feedVersionId}/calendars")
    public ServiceCalendar create(@PathVariable UUID feedVersionId, @RequestBody ServiceCalendar calendar) {
        FeedVersion feedVersion = feedVersionRepository.findById(feedVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("FeedVersion not found: " + feedVersionId));
        calendar.setId(null);
        calendar.setFeedVersion(feedVersion);
        if (calendar.getGtfsId() == null || calendar.getGtfsId().isBlank()) {
            calendar.setGtfsId(idGenerator.next("service_calendar", "SVC", feedVersionId, "feed_version_id"));
        }
        OffsetDateTime now = OffsetDateTime.now();
        calendar.setCreatedAt(now);
        calendar.setUpdatedAt(now);
        return serviceCalendarRepository.save(calendar);
    }

    @PutMapping("/api/v1/calendars/{id}")
    public ServiceCalendar update(@PathVariable UUID id, @RequestBody ServiceCalendar update) {
        ServiceCalendar existing = serviceCalendarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceCalendar not found: " + id));
        existing.setGtfsId(update.getGtfsId());
        existing.setName(update.getName());
        existing.setMonday(update.getMonday());
        existing.setTuesday(update.getTuesday());
        existing.setWednesday(update.getWednesday());
        existing.setThursday(update.getThursday());
        existing.setFriday(update.getFriday());
        existing.setSaturday(update.getSaturday());
        existing.setSunday(update.getSunday());
        existing.setStartDate(update.getStartDate());
        existing.setEndDate(update.getEndDate());
        existing.setUpdatedAt(OffsetDateTime.now());
        return serviceCalendarRepository.save(existing);
    }

    @DeleteMapping("/api/v1/calendars/{id}")
    public void delete(@PathVariable UUID id) {
        serviceCalendarRepository.deleteById(id);
    }

    @GetMapping("/api/v1/calendars/{id}/exceptions")
    public List<ServiceException> listExceptions(@PathVariable UUID id) {
        return serviceExceptionRepository.findByServiceCalendarId(id);
    }

    @PostMapping("/api/v1/calendars/{id}/exceptions")
    public ServiceException createException(@PathVariable UUID id, @RequestBody ServiceException exception) {
        ServiceCalendar calendar = serviceCalendarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ServiceCalendar not found: " + id));
        exception.setId(null);
        exception.setServiceCalendar(calendar);
        return serviceExceptionRepository.save(exception);
    }

    @DeleteMapping("/api/v1/service-exceptions/{id}")
    public void deleteException(@PathVariable UUID id) {
        serviceExceptionRepository.deleteById(id);
    }
}
