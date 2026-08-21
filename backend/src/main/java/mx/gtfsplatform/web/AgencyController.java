package mx.gtfsplatform.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.Agency;
import mx.gtfsplatform.domain.FeedVersion;
import mx.gtfsplatform.gtfs.GtfsIdGenerator;
import mx.gtfsplatform.repository.AgencyRepository;
import mx.gtfsplatform.repository.FeedVersionRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AgencyController {

    private final AgencyRepository agencyRepository;
    private final FeedVersionRepository feedVersionRepository;
    private final GtfsIdGenerator idGenerator;

    public AgencyController(AgencyRepository agencyRepository, FeedVersionRepository feedVersionRepository,
            GtfsIdGenerator idGenerator) {
        this.agencyRepository = agencyRepository;
        this.feedVersionRepository = feedVersionRepository;
        this.idGenerator = idGenerator;
    }

    @GetMapping("/api/v1/feed-versions/{feedVersionId}/agencies")
    public List<Agency> list(@PathVariable UUID feedVersionId) {
        return agencyRepository.findByFeedVersionId(feedVersionId);
    }

    @PostMapping("/api/v1/feed-versions/{feedVersionId}/agencies")
    public Agency create(@PathVariable UUID feedVersionId, @RequestBody Agency agency) {
        FeedVersion feedVersion = feedVersionRepository.findById(feedVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("FeedVersion not found: " + feedVersionId));
        agency.setId(null);
        agency.setFeedVersion(feedVersion);
        if (agency.getGtfsId() == null || agency.getGtfsId().isBlank()) {
            agency.setGtfsId(idGenerator.next("agency", "AGENCY", feedVersionId, "feed_version_id"));
        }
        OffsetDateTime now = OffsetDateTime.now();
        agency.setCreatedAt(now);
        agency.setUpdatedAt(now);
        return agencyRepository.save(agency);
    }

    @PutMapping("/api/v1/agencies/{id}")
    public Agency update(@PathVariable UUID id, @RequestBody Agency update) {
        Agency existing = agencyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agency not found: " + id));
        existing.setGtfsId(update.getGtfsId());
        existing.setAgencyName(update.getAgencyName());
        existing.setAgencyUrl(update.getAgencyUrl());
        existing.setAgencyTimezone(update.getAgencyTimezone());
        existing.setAgencyLang(update.getAgencyLang());
        existing.setAgencyPhone(update.getAgencyPhone());
        existing.setAgencyFareUrl(update.getAgencyFareUrl());
        existing.setAgencyEmail(update.getAgencyEmail());
        existing.setUpdatedAt(OffsetDateTime.now());
        existing.setUpdatedBy(update.getUpdatedBy());
        return agencyRepository.save(existing);
    }

    @DeleteMapping("/api/v1/agencies/{id}")
    public void delete(@PathVariable UUID id) {
        agencyRepository.deleteById(id);
    }
}
