package mx.gtfsplatform.gtfs.kml;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import mx.gtfsplatform.domain.FeedVersion;
import mx.gtfsplatform.domain.PatternStop;
import mx.gtfsplatform.domain.RoutePattern;
import mx.gtfsplatform.domain.ShapePoint;
import mx.gtfsplatform.domain.Stop;
import mx.gtfsplatform.geo.GeoUtils;
import mx.gtfsplatform.geocoding.GeocodingProvider;
import mx.gtfsplatform.gtfs.GtfsIdGenerator;
import mx.gtfsplatform.repository.FeedVersionRepository;
import mx.gtfsplatform.repository.PatternStopRepository;
import mx.gtfsplatform.repository.RoutePatternRepository;
import mx.gtfsplatform.repository.ShapePointRepository;
import mx.gtfsplatform.repository.StopRepository;
import mx.gtfsplatform.repository.TripRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Importar paradas y rutas desde archivos KML. Dos flujos independientes:
 * 1) importStops: cada Placemark-Point del KML se convierte en una parada, nombrada por
 *    intersección más cercana — el mismo GeocodingProvider que usa el formulario manual
 *    de "nueva parada" en el mapa (sección 6), así que el resultado es indistinguible de
 *    si el usuario las hubiera creado a mano una por una.
 * 2) importRouteAndMatch: el Placemark-LineString del KML se guarda como shape del
 *    pattern, y se recorre la lista de paradas YA existentes del feed (deben importarse
 *    primero) para quedarse con las que caen a poca distancia del trazo — ordenadas por
 *    su posición a lo largo de la línea — y arma con ellas la secuencia de pattern_stop.
 */
@Service
public class KmlImportService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final StopRepository stopRepository;
    private final FeedVersionRepository feedVersionRepository;
    private final GtfsIdGenerator idGenerator;
    private final GeocodingProvider geocodingProvider;
    private final RoutePatternRepository routePatternRepository;
    private final PatternStopRepository patternStopRepository;
    private final ShapePointRepository shapePointRepository;
    private final TripRepository tripRepository;

    public KmlImportService(StopRepository stopRepository, FeedVersionRepository feedVersionRepository,
            GtfsIdGenerator idGenerator, GeocodingProvider geocodingProvider,
            RoutePatternRepository routePatternRepository, PatternStopRepository patternStopRepository,
            ShapePointRepository shapePointRepository, TripRepository tripRepository) {
        this.stopRepository = stopRepository;
        this.feedVersionRepository = feedVersionRepository;
        this.idGenerator = idGenerator;
        this.geocodingProvider = geocodingProvider;
        this.routePatternRepository = routePatternRepository;
        this.patternStopRepository = patternStopRepository;
        this.shapePointRepository = shapePointRepository;
        this.tripRepository = tripRepository;
    }

    @Transactional
    public StopsImportResult importStops(UUID feedVersionId, InputStream kml) throws Exception {
        FeedVersion feedVersion = feedVersionRepository.findById(feedVersionId)
                .orElseThrow(() -> new NoSuchElementException("feed_version no encontrado: " + feedVersionId));

        List<KmlParser.KmlPoint> points = KmlParser.parsePoints(kml);
        List<StopSummary> created = new ArrayList<>();
        int geocoded = 0;
        for (KmlParser.KmlPoint p : points) {
            Optional<String> suggestion;
            try {
                suggestion = geocodingProvider.suggestStopName(p.lat(), p.lon());
            } catch (RuntimeException e) {
                // El proveedor de geocoding es best-effort (sección 6): si falla una
                // consulta puntual, la parada se crea igual con un nombre de respaldo
                // en vez de tirar todo el import por un punto problemático.
                suggestion = Optional.empty();
            }
            String name;
            boolean wasGeocoded;
            if (suggestion.isPresent()) {
                name = suggestion.get();
                wasGeocoded = true;
                geocoded++;
            } else if (p.name() != null && !p.name().isBlank()) {
                name = p.name();
                wasGeocoded = false;
            } else {
                name = "Parada importada";
                wasGeocoded = false;
            }

            Stop stop = new Stop();
            stop.setFeedVersion(feedVersion);
            stop.setGtfsId(idGenerator.next("stop", "STOP", feedVersionId, "feed_version_id"));
            stop.setStopName(name);
            stop.setGeom(GEOMETRY_FACTORY.createPoint(new Coordinate(p.lon(), p.lat())));
            stop.setLocationType((short) 0);
            stop.setWheelchairBoarding((short) 0);
            stop.setRowVersion(0L);
            OffsetDateTime now = OffsetDateTime.now();
            stop.setCreatedAt(now);
            stop.setUpdatedAt(now);
            // saveAndFlush, no save: GtfsIdGenerator.next() cuenta filas con JDBC crudo
            // fuera de la sesión de Hibernate — sin forzar el flush aquí, no ve el
            // INSERT anterior (que Hibernate difiere hasta el commit) y le asigna el
            // mismo gtfs_id a dos paradas seguidas, violando la unique constraint al
            // hacer flush al final. Bug real detectado importando 3 puntos de un KML.
            stop = stopRepository.saveAndFlush(stop);

            created.add(new StopSummary(stop.getId().toString(), stop.getStopName(), p.lat(), p.lon(), wasGeocoded));
        }
        return new StopsImportResult(points.size(), geocoded, created);
    }

    @Transactional
    public PatternImportResult importRouteAndMatch(UUID routePatternId, InputStream kml, double matchRadiusMeters)
            throws Exception {
        RoutePattern pattern = routePatternRepository.findById(routePatternId)
                .orElseThrow(() -> new NoSuchElementException("route_pattern no encontrado: " + routePatternId));
        UUID feedVersionId = pattern.getRoute().getFeedVersion().getId();

        List<KmlParser.KmlLine> lines = KmlParser.parseLines(kml);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("El KML no contiene ningún trazo (LineString)");
        }
        // Si el KML trae varias geometrías sueltas, la más larga suele ser el recorrido
        // real (las demás podrían ser tramos auxiliares, marcadores de ida/vuelta, etc.).
        KmlParser.KmlLine line = lines.stream()
                .max(Comparator.comparingInt(l -> l.lats().length))
                .orElseThrow();

        double[] cumulative = GeoUtils.cumulativeDistancesMeters(line.lats(), line.lons());

        record Candidate(Stop stop, double alongMeters, double distanceMeters) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (Stop stop : stopRepository.findByFeedVersionId(feedVersionId)) {
            GeoUtils.Projection proj = GeoUtils.projectPointOntoPolyline(
                    stop.getStopLat(), stop.getStopLon(), line.lats(), line.lons());
            if (proj.distanceMeters() <= matchRadiusMeters) {
                candidates.add(new Candidate(stop, GeoUtils.distanceAlongPolylineMeters(proj, cumulative),
                        proj.distanceMeters()));
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::alongMeters));

        // Mismo motivo que RoutePatternController.replacePatternStops/replaceShapePoints:
        // cambiar el recorrido invalida cualquier trip generado sobre el anterior, y hay
        // que forzar el flush antes de reinsertar para no chocar con las unique
        // constraints (route_pattern_id, stop_sequence)/(route_pattern_id, shape_pt_sequence).
        tripRepository.deleteByRoutePatternId(routePatternId);
        tripRepository.flush();
        patternStopRepository.deleteByRoutePatternId(routePatternId);
        patternStopRepository.flush();

        List<PatternStop> patternStops = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            patternStops.add(PatternStop.builder()
                    .routePattern(pattern)
                    .stop(c.stop())
                    .stopSequence(i)
                    .defaultTimepoint((short) 1)
                    .defaultPickupType((short) 0)
                    .defaultDropOffType((short) 0)
                    .build());
        }
        patternStopRepository.saveAll(patternStops);

        shapePointRepository.deleteByRoutePatternId(routePatternId);
        shapePointRepository.flush();
        List<ShapePoint> shapePoints = new ArrayList<>();
        for (int i = 0; i < line.lats().length; i++) {
            shapePoints.add(ShapePoint.builder()
                    .routePattern(pattern)
                    .shapePtLat(line.lats()[i])
                    .shapePtLon(line.lons()[i])
                    .shapePtSequence(i)
                    .build());
        }
        shapePointRepository.saveAll(shapePoints);

        List<MatchedStopSummary> matchedSummaries = candidates.stream()
                .map(c -> new MatchedStopSummary(c.stop().getId().toString(), c.stop().getStopName(),
                        Math.round(c.distanceMeters() * 10) / 10.0))
                .toList();
        return new PatternImportResult(shapePoints.size(), matchedSummaries.size(), matchRadiusMeters, matchedSummaries);
    }

    public record StopSummary(String id, String name, double lat, double lon, boolean geocoded) {
    }

    public record StopsImportResult(int totalPoints, int geocodedCount, List<StopSummary> stops) {
    }

    public record MatchedStopSummary(String id, String name, double distanceMeters) {
    }

    public record PatternImportResult(
            int shapePointCount, int matchedStopCount, double matchRadiusMeters, List<MatchedStopSummary> matchedStops) {
    }
}
