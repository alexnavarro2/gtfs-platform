package mx.gtfsplatform.web;

import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.PatternStop;
import mx.gtfsplatform.domain.Route;
import mx.gtfsplatform.domain.RoutePattern;
import mx.gtfsplatform.domain.ShapePoint;
import mx.gtfsplatform.domain.Stop;
import mx.gtfsplatform.gtfs.GtfsIdGenerator;
import mx.gtfsplatform.repository.PatternStopRepository;
import mx.gtfsplatform.repository.RoutePatternRepository;
import mx.gtfsplatform.repository.RouteRepository;
import mx.gtfsplatform.repository.ShapePointRepository;
import mx.gtfsplatform.repository.StopRepository;
import mx.gtfsplatform.repository.TripRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

@RestController
public class RoutePatternController {

    private final RoutePatternRepository routePatternRepository;
    private final RouteRepository routeRepository;
    private final ShapePointRepository shapePointRepository;
    private final PatternStopRepository patternStopRepository;
    private final StopRepository stopRepository;
    private final TripRepository tripRepository;
    private final GtfsIdGenerator idGenerator;

    public RoutePatternController(RoutePatternRepository routePatternRepository, RouteRepository routeRepository,
            ShapePointRepository shapePointRepository, PatternStopRepository patternStopRepository,
            StopRepository stopRepository, TripRepository tripRepository, GtfsIdGenerator idGenerator) {
        this.routePatternRepository = routePatternRepository;
        this.routeRepository = routeRepository;
        this.shapePointRepository = shapePointRepository;
        this.patternStopRepository = patternStopRepository;
        this.stopRepository = stopRepository;
        this.tripRepository = tripRepository;
        this.idGenerator = idGenerator;
    }

    @GetMapping("/api/v1/routes/{routeId}/patterns")
    public List<RoutePattern> list(@PathVariable UUID routeId) {
        return routePatternRepository.findByRouteId(routeId);
    }

    @PostMapping("/api/v1/routes/{routeId}/patterns")
    public RoutePattern create(@PathVariable UUID routeId, @RequestBody RoutePattern routePattern) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found: " + routeId));
        routePattern.setId(null);
        routePattern.setRoute(route);
        if (routePattern.getShapeGtfsId() == null || routePattern.getShapeGtfsId().isBlank()) {
            routePattern.setShapeGtfsId(idGenerator.next("route_pattern", "shape_gtfs_id", "SHAPE", routeId, "route_id"));
        }
        if (routePattern.getRowVersion() == null) {
            routePattern.setRowVersion(0L);
        }
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        routePattern.setCreatedAt(now);
        routePattern.setUpdatedAt(now);
        return routePatternRepository.save(routePattern);
    }

    @PutMapping("/api/v1/patterns/{id}")
    public RoutePattern update(@PathVariable UUID id, @RequestBody RoutePattern update) {
        RoutePattern existing = routePatternRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RoutePattern not found: " + id));
        existing.setShapeGtfsId(update.getShapeGtfsId());
        existing.setName(update.getName());
        existing.setDirectionId(update.getDirectionId());
        existing.setTripHeadsign(update.getTripHeadsign());
        existing.setTripShortName(update.getTripShortName());
        return routePatternRepository.save(existing);
    }

    @DeleteMapping("/api/v1/patterns/{id}")
    public void delete(@PathVariable UUID id) {
        routePatternRepository.deleteById(id);
    }

    @GetMapping("/api/v1/patterns/{id}/shape-points")
    public List<ShapePoint> getShapePoints(@PathVariable UUID id) {
        return shapePointRepository.findByRoutePatternIdOrderByShapePtSequenceAsc(id);
    }

    @GetMapping("/api/v1/patterns/{id}/stops")
    public List<PatternStop> getPatternStops(@PathVariable UUID id) {
        return patternStopRepository.findByRoutePatternIdOrderByStopSequenceAsc(id);
    }

    @PutMapping("/api/v1/patterns/{id}/shape-points")
    @Transactional
    public List<ShapePoint> replaceShapePoints(@PathVariable UUID id, @RequestBody List<LatLon> points) {
        RoutePattern routePattern = routePatternRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RoutePattern not found: " + id));
        shapePointRepository.deleteByRoutePatternId(id);
        // Sin este flush, Hibernate puede reordenar el INSERT antes del DELETE dentro
        // del mismo flush automático y violar la unique (route_pattern_id, shape_pt_sequence)
        // al reusar las mismas secuencias que se están reemplazando.
        shapePointRepository.flush();
        List<ShapePoint> shapePoints = points.stream()
                .map(p -> ShapePoint.builder()
                        .routePattern(routePattern)
                        .shapePtLat(p.lat())
                        .shapePtLon(p.lon())
                        .shapeDistTraveled(null)
                        .build())
                .toList();
        for (int i = 0; i < shapePoints.size(); i++) {
            shapePoints.get(i).setShapePtSequence(i);
        }
        return shapePointRepository.saveAll(shapePoints);
    }

    @PutMapping("/api/v1/patterns/{id}/stops")
    @Transactional
    public List<PatternStop> replacePatternStops(@PathVariable UUID id, @RequestBody List<StopRef> stopRefs) {
        RoutePattern routePattern = routePatternRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RoutePattern not found: " + id));
        // Cambiar las paradas invalida cualquier trip/stop_time generado sobre el orden
        // anterior (stop_time.pattern_stop_id no tiene ON DELETE CASCADE a propósito,
        // para no borrar horarios por accidente desde ningún otro flujo) — el usuario
        // debe regenerar el horario después de reordenar/cambiar paradas.
        tripRepository.deleteByRoutePatternId(id);
        tripRepository.flush();
        patternStopRepository.deleteByRoutePatternId(id);
        // Mismo motivo que en replaceShapePoints: forzar el DELETE antes de insertar
        // filas nuevas que reusan las mismas (route_pattern_id, stop_sequence).
        patternStopRepository.flush();
        List<PatternStop> patternStops = stopRefs.stream()
                .map(ref -> {
                    Stop stop = stopRepository.findById(ref.stopId())
                            .orElseThrow(() -> new ResourceNotFoundException("Stop not found: " + ref.stopId()));
                    return PatternStop.builder()
                            .routePattern(routePattern)
                            .stop(stop)
                            .defaultTimepoint((short) 1)
                            .defaultPickupType((short) 0)
                            .defaultDropOffType((short) 0)
                            .build();
                })
                .toList();
        for (int i = 0; i < patternStops.size(); i++) {
            patternStops.get(i).setStopSequence(i);
        }
        return patternStopRepository.saveAll(patternStops);
    }

    public record LatLon(Double lat, Double lon) {
    }

    public record StopRef(UUID stopId) {
    }
}
