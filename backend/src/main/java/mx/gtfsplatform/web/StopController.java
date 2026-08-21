package mx.gtfsplatform.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.FeedVersion;
import mx.gtfsplatform.domain.Stop;
import mx.gtfsplatform.gtfs.GtfsIdGenerator;
import mx.gtfsplatform.repository.FeedVersionRepository;
import mx.gtfsplatform.repository.StopRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StopController {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final StopRepository stopRepository;
    private final FeedVersionRepository feedVersionRepository;
    private final GtfsIdGenerator idGenerator;

    public StopController(StopRepository stopRepository, FeedVersionRepository feedVersionRepository,
            GtfsIdGenerator idGenerator) {
        this.stopRepository = stopRepository;
        this.feedVersionRepository = feedVersionRepository;
        this.idGenerator = idGenerator;
    }

    @GetMapping("/api/v1/feed-versions/{feedVersionId}/stops")
    public List<Stop> list(@PathVariable UUID feedVersionId) {
        return stopRepository.findByFeedVersionId(feedVersionId);
    }

    @PostMapping("/api/v1/feed-versions/{feedVersionId}/stops")
    public Stop create(@PathVariable UUID feedVersionId, @RequestBody StopRequest request) {
        FeedVersion feedVersion = feedVersionRepository.findById(feedVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("FeedVersion not found: " + feedVersionId));
        Stop stop = new Stop();
        stop.setFeedVersion(feedVersion);
        stop.setGtfsId(request.gtfsId() != null && !request.gtfsId().isBlank()
                ? request.gtfsId()
                : idGenerator.next("stop", "STOP", feedVersionId, "feed_version_id"));
        stop.setStopCode(request.stopCode());
        stop.setStopName(request.stopName());
        stop.setTtsStopName(request.ttsStopName());
        stop.setStopDesc(request.stopDesc());
        stop.setGeom(toPoint(request.stopLat(), request.stopLon()));
        stop.setZoneId(request.zoneId());
        stop.setStopUrl(request.stopUrl());
        stop.setLocationType(request.locationType() != null ? request.locationType() : 0);
        if (request.parentStationId() != null) {
            stopRepository.findById(request.parentStationId()).ifPresent(stop::setParentStation);
        }
        stop.setStopTimezone(request.stopTimezone());
        stop.setWheelchairBoarding(request.wheelchairBoarding() != null ? request.wheelchairBoarding() : 0);
        stop.setPlatformCode(request.platformCode());
        stop.setRowVersion(0L);
        OffsetDateTime now = OffsetDateTime.now();
        stop.setCreatedAt(now);
        stop.setUpdatedAt(now);
        return stopRepository.save(stop);
    }

    @PutMapping("/api/v1/stops/{id}")
    public Stop update(@PathVariable UUID id, @RequestBody StopRequest request) {
        Stop existing = stopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stop not found: " + id));
        if (request.gtfsId() != null) {
            existing.setGtfsId(request.gtfsId());
        }
        if (request.stopCode() != null) {
            existing.setStopCode(request.stopCode());
        }
        if (request.stopName() != null) {
            existing.setStopName(request.stopName());
        }
        if (request.ttsStopName() != null) {
            existing.setTtsStopName(request.ttsStopName());
        }
        if (request.stopDesc() != null) {
            existing.setStopDesc(request.stopDesc());
        }
        if (request.stopLat() != null && request.stopLon() != null) {
            existing.setGeom(toPoint(request.stopLat(), request.stopLon()));
        }
        if (request.zoneId() != null) {
            existing.setZoneId(request.zoneId());
        }
        if (request.stopUrl() != null) {
            existing.setStopUrl(request.stopUrl());
        }
        if (request.locationType() != null) {
            existing.setLocationType(request.locationType());
        }
        if (request.parentStationId() != null) {
            stopRepository.findById(request.parentStationId()).ifPresent(existing::setParentStation);
        }
        if (request.stopTimezone() != null) {
            existing.setStopTimezone(request.stopTimezone());
        }
        if (request.wheelchairBoarding() != null) {
            existing.setWheelchairBoarding(request.wheelchairBoarding());
        }
        if (request.platformCode() != null) {
            existing.setPlatformCode(request.platformCode());
        }
        existing.setUpdatedAt(OffsetDateTime.now());
        return stopRepository.save(existing);
    }

    @DeleteMapping("/api/v1/stops/{id}")
    public void delete(@PathVariable UUID id) {
        stopRepository.deleteById(id);
    }

    @GetMapping("/api/v1/stops/near")
    public List<Stop> near(@RequestParam double lat, @RequestParam double lon,
                            @RequestParam double radiusMeters) {
        return stopRepository.findNear(lat, lon, radiusMeters);
    }

    private static Point toPoint(Double lat, Double lon) {
        if (lat == null || lon == null) {
            return null;
        }
        return GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));
    }

    public record StopRequest(
            String gtfsId,
            String stopCode,
            String stopName,
            String ttsStopName,
            String stopDesc,
            Double stopLat,
            Double stopLon,
            String zoneId,
            String stopUrl,
            Short locationType,
            UUID parentStationId,
            String stopTimezone,
            Short wheelchairBoarding,
            String platformCode) {
    }
}
