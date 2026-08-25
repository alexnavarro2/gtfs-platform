package mx.gtfsplatform.gtfs.kml;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import mx.gtfsplatform.domain.Agency;
import mx.gtfsplatform.domain.FeedVersion;
import mx.gtfsplatform.domain.PatternStop;
import mx.gtfsplatform.domain.Route;
import mx.gtfsplatform.domain.RoutePattern;
import mx.gtfsplatform.domain.ShapePoint;
import mx.gtfsplatform.domain.Stop;
import mx.gtfsplatform.geo.GeoUtils;
import mx.gtfsplatform.geocoding.GeocodingProvider;
import mx.gtfsplatform.gtfs.GtfsIdGenerator;
import mx.gtfsplatform.repository.AgencyRepository;
import mx.gtfsplatform.repository.FeedVersionRepository;
import mx.gtfsplatform.repository.PatternStopRepository;
import mx.gtfsplatform.repository.RoutePatternRepository;
import mx.gtfsplatform.repository.RouteRepository;
import mx.gtfsplatform.repository.ShapePointRepository;
import mx.gtfsplatform.repository.StopRepository;
import mx.gtfsplatform.repository.TripRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Importar paradas y rutas desde archivos KML. Tres flujos:
 * 1) importStops: cada Placemark-Point del KML se convierte en una parada, nombrada por
 *    intersección más cercana — el mismo GeocodingProvider que usa el formulario manual
 *    de "nueva parada" en el mapa (sección 6), así que el resultado es indistinguible de
 *    si el usuario las hubiera creado a mano una por una.
 * 2) importRouteAndMatch: el Placemark-LineString del KML (el más largo si hay varios) se
 *    guarda como shape de UN pattern ya existente, emparejando paradas por cercanía.
 * 3) importRoutesFromKml: variante para un KML con VARIAS rutas — crea una ruta + sentido
 *    nuevo por cada LineString del KML (usando el nombre del Placemark), en vez de
 *    limitarse al sentido que el usuario ya tenga abierto.
 */
@Service
public class KmlImportService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    // Paleta simple para que las rutas creadas en lote no salgan todas del mismo color —
    // el usuario puede cambiarlo después desde "Editar ruta" igual que cualquier otra.
    private static final String[] ROUTE_COLOR_PALETTE = {
        "1E88E5", "E53935", "43A047", "FB8C00", "8E24AA", "00897B", "5D4037", "3949AB",
    };

    private final StopRepository stopRepository;
    private final FeedVersionRepository feedVersionRepository;
    private final GtfsIdGenerator idGenerator;
    private final GeocodingProvider geocodingProvider;
    private final AgencyRepository agencyRepository;
    private final RouteRepository routeRepository;
    private final RoutePatternRepository routePatternRepository;
    private final PatternStopRepository patternStopRepository;
    private final ShapePointRepository shapePointRepository;
    private final TripRepository tripRepository;

    public KmlImportService(StopRepository stopRepository, FeedVersionRepository feedVersionRepository,
            GtfsIdGenerator idGenerator, GeocodingProvider geocodingProvider, AgencyRepository agencyRepository,
            RouteRepository routeRepository, RoutePatternRepository routePatternRepository,
            PatternStopRepository patternStopRepository, ShapePointRepository shapePointRepository,
            TripRepository tripRepository) {
        this.stopRepository = stopRepository;
        this.feedVersionRepository = feedVersionRepository;
        this.idGenerator = idGenerator;
        this.geocodingProvider = geocodingProvider;
        this.agencyRepository = agencyRepository;
        this.routeRepository = routeRepository;
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

        return applyLineToPattern(pattern, feedVersionId, line, matchRadiusMeters);
    }

    @Transactional
    public BulkRoutesImportResult importRoutesFromKml(
            UUID feedVersionId, UUID agencyId, InputStream kml, double matchRadiusMeters) throws Exception {
        FeedVersion feedVersion = feedVersionRepository.findById(feedVersionId)
                .orElseThrow(() -> new NoSuchElementException("feed_version no encontrado: " + feedVersionId));
        Agency agency = agencyRepository.findById(agencyId)
                .orElseThrow(() -> new NoSuchElementException("agencia no encontrada: " + agencyId));

        List<KmlParser.KmlLine> lines = KmlParser.parseLines(kml);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("El KML no contiene ningún trazo (LineString)");
        }

        List<RouteImportResult> results = new ArrayList<>();
        for (KmlParser.KmlLine line : lines) {
            String name = (line.name() != null && !line.name().isBlank())
                    ? line.name().trim()
                    : "Ruta importada " + (results.size() + 1);
            OffsetDateTime now = OffsetDateTime.now();

            Route route = new Route();
            route.setFeedVersion(feedVersion);
            route.setAgency(agency);
            route.setGtfsId(idGenerator.next("route", "ROUTE", feedVersionId, "feed_version_id"));
            route.setRouteShortName(name);
            route.setRouteType(3); // bus — mismo default que "Nueva ruta" en el editor manual
            route.setRouteColor(ROUTE_COLOR_PALETTE[results.size() % ROUTE_COLOR_PALETTE.length]);
            route.setRouteTextColor("FFFFFF");
            route.setCreatedAt(now);
            route.setUpdatedAt(now);
            // saveAndFlush: mismo motivo que en importStops — el siguiente idGenerator.next()
            // (para el pattern, y para la siguiente ruta del lote) necesita ver este INSERT.
            route = routeRepository.saveAndFlush(route);

            RoutePattern pattern = RoutePattern.builder()
                    .route(route)
                    .shapeGtfsId(idGenerator.next("route_pattern", "shape_gtfs_id", "SHAPE", route.getId(), "route_id"))
                    .name("IDA")
                    .directionId((short) 0)
                    .rowVersion(0L)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            pattern = routePatternRepository.saveAndFlush(pattern);

            PatternImportResult patternResult = applyLineToPattern(pattern, feedVersionId, line, matchRadiusMeters);
            results.add(new RouteImportResult(
                    route.getId().toString(), route.getRouteShortName(), pattern.getId().toString(), patternResult));
        }
        return new BulkRoutesImportResult(results.size(), results);
    }

    /** Guarda el trazo de una línea KML como shape del pattern y le empareja paradas cercanas. */
    private PatternImportResult applyLineToPattern(
            RoutePattern pattern, UUID feedVersionId, KmlParser.KmlLine line, double matchRadiusMeters) {
        UUID routePatternId = pattern.getId();
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
        // En un pattern recién creado esto no borra nada (no tiene trips/stops todavía),
        // pero deja el mismo código servir ambos flujos sin duplicarlo.
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

    public record RouteImportResult(
            String routeId, String routeName, String patternId, PatternImportResult pattern) {
    }

    public record BulkRoutesImportResult(int routeCount, List<RouteImportResult> routes) {
    }
}
