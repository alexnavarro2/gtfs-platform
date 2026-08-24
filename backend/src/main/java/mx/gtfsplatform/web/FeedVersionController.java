package mx.gtfsplatform.web;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.Feed;
import mx.gtfsplatform.domain.FeedVersion;
import mx.gtfsplatform.domain.FeedVersionStatus;
import mx.gtfsplatform.repository.FeedRepository;
import mx.gtfsplatform.repository.FeedVersionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeedVersionController {

    private final FeedVersionRepository feedVersionRepository;
    private final FeedRepository feedRepository;

    public FeedVersionController(FeedVersionRepository feedVersionRepository, FeedRepository feedRepository) {
        this.feedVersionRepository = feedVersionRepository;
        this.feedRepository = feedRepository;
    }

    @GetMapping("/api/v1/feeds/{feedId}/versions")
    public List<FeedVersion> list(@PathVariable UUID feedId) {
        return feedVersionRepository.findByFeedId(feedId);
    }

    @PostMapping("/api/v1/feeds/{feedId}/versions")
    public FeedVersion create(@PathVariable UUID feedId) {
        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() -> new ResourceNotFoundException("Feed not found: " + feedId));
        int nextVersionNumber = feedVersionRepository.findByFeedId(feedId).stream()
                .map(FeedVersion::getVersionNumber)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
        OffsetDateTime now = OffsetDateTime.now();
        FeedVersion feedVersion = FeedVersion.builder()
                .feed(feed)
                .versionNumber(nextVersionNumber)
                .status(FeedVersionStatus.DRAFT)
                .rowVersion(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return feedVersionRepository.save(feedVersion);
    }

    @GetMapping("/api/v1/feed-versions/{id}")
    public FeedVersion get(@PathVariable UUID id) {
        return feedVersionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FeedVersion not found: " + id));
    }

    // Único punto para completar feed_info.txt (feed_publisher_name/url/lang/etc.).
    // Sin esto, un feed creado desde el portal (a diferencia de uno reimportado
    // desde un zip que ya traía feed_info.txt) nunca podía llenar estos campos,
    // y GtfsExportServiceImpl.writeFeedInfo() omite el archivo en silencio si
    // feedPublisherName es null — el feed exportaba sin feed_info.txt.
    @PutMapping("/api/v1/feed-versions/{id}/feed-info")
    public FeedVersion updateFeedInfo(@PathVariable UUID id, @RequestBody FeedInfoRequest request) {
        FeedVersion existing = feedVersionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FeedVersion not found: " + id));
        existing.setFeedPublisherName(request.feedPublisherName());
        existing.setFeedPublisherUrl(request.feedPublisherUrl());
        existing.setFeedLang(request.feedLang());
        existing.setDefaultLang(request.defaultLang());
        existing.setFeedStartDate(request.feedStartDate());
        existing.setFeedEndDate(request.feedEndDate());
        existing.setFeedVersionString(request.feedVersionString());
        existing.setFeedContactEmail(request.feedContactEmail());
        existing.setFeedContactUrl(request.feedContactUrl());
        existing.setUpdatedAt(OffsetDateTime.now());
        return feedVersionRepository.save(existing);
    }

    public record FeedInfoRequest(
            String feedPublisherName,
            String feedPublisherUrl,
            String feedLang,
            String defaultLang,
            LocalDate feedStartDate,
            LocalDate feedEndDate,
            String feedVersionString,
            String feedContactEmail,
            String feedContactUrl) {
    }
}
