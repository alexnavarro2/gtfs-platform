package mx.gtfsplatform.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.FeedVersion;
import mx.gtfsplatform.domain.Route;
import mx.gtfsplatform.gtfs.GtfsIdGenerator;
import mx.gtfsplatform.repository.FeedVersionRepository;
import mx.gtfsplatform.repository.RouteRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RouteController {

    private final RouteRepository routeRepository;
    private final FeedVersionRepository feedVersionRepository;
    private final GtfsIdGenerator idGenerator;

    public RouteController(RouteRepository routeRepository, FeedVersionRepository feedVersionRepository,
            GtfsIdGenerator idGenerator) {
        this.routeRepository = routeRepository;
        this.feedVersionRepository = feedVersionRepository;
        this.idGenerator = idGenerator;
    }

    @GetMapping("/api/v1/feed-versions/{feedVersionId}/routes")
    public List<Route> list(@PathVariable UUID feedVersionId) {
        return routeRepository.findByFeedVersionId(feedVersionId);
    }

    @PostMapping("/api/v1/feed-versions/{feedVersionId}/routes")
    public Route create(@PathVariable UUID feedVersionId, @RequestBody Route route) {
        FeedVersion feedVersion = feedVersionRepository.findById(feedVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("FeedVersion not found: " + feedVersionId));
        route.setId(null);
        route.setFeedVersion(feedVersion);
        if (route.getGtfsId() == null || route.getGtfsId().isBlank()) {
            route.setGtfsId(idGenerator.next("route", "ROUTE", feedVersionId, "feed_version_id"));
        }
        OffsetDateTime now = OffsetDateTime.now();
        route.setCreatedAt(now);
        route.setUpdatedAt(now);
        return routeRepository.save(route);
    }

    @PutMapping("/api/v1/routes/{id}")
    public Route update(@PathVariable UUID id, @RequestBody Route update) {
        Route existing = routeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found: " + id));
        existing.setGtfsId(update.getGtfsId());
        existing.setAgency(update.getAgency());
        existing.setRouteShortName(update.getRouteShortName());
        existing.setRouteLongName(update.getRouteLongName());
        existing.setRouteDesc(update.getRouteDesc());
        existing.setRouteType(update.getRouteType());
        existing.setRouteUrl(update.getRouteUrl());
        existing.setRouteColor(update.getRouteColor());
        existing.setRouteTextColor(update.getRouteTextColor());
        existing.setRouteSortOrder(update.getRouteSortOrder());
        existing.setContinuousPickup(update.getContinuousPickup());
        existing.setContinuousDropOff(update.getContinuousDropOff());
        existing.setUpdatedAt(OffsetDateTime.now());
        existing.setUpdatedBy(update.getUpdatedBy());
        return routeRepository.save(existing);
    }

    @DeleteMapping("/api/v1/routes/{id}")
    public void delete(@PathVariable UUID id) {
        routeRepository.deleteById(id);
    }
}
