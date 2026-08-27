package mx.gtfsplatform.gtfs.kml;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import mx.gtfsplatform.domain.Agency;
import mx.gtfsplatform.domain.FeedVersion;
import mx.gtfsplatform.domain.KmlStopImportJob;
import mx.gtfsplatform.domain.PatternStop;
import mx.gtfsplatform.domain.Route;
import mx.gtfsplatform.domain.RoutePattern;
import mx.gtfsplatform.domain.ShapePoint;
import mx.gtfsplatform.domain.Stop;
import mx.gtfsplatform.geo.GeoUtils;
import mx.gtfsplatform.gtfs.GtfsIdGenerator;
import mx.gtfsplatform.repository.AgencyRepository;
import mx.gtfsplatform.repository.FeedVersionRepository;
import mx.gtfsplatform.repository.KmlStopImportJobRepository;
import mx.gtfsplatform.repository.PatternStopRepository;
import mx.gtfsplatform.repository.RoutePatternRepository;
import mx.gtfsplatform.repository.RouteRepository;
import mx.gtfsplatform.repository.ShapePointRepository;
import mx.gtfsplatform.repository.StopRepository;
import mx.gtfsplatform.repository.TripRepository;
import mx.gtfsplatform.routing.RoutingProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Importar paradas y rutas desde archivos KML. Tres flujos:
 * 1) startStopsImport: cada Placemark-Point del KML se convierte en una parada, nombrada
 *    por intersección más cercana — el mismo GeocodingProvider que usa el formulario
 *    manual de "nueva parada" en el mapa (sección 6). Corre en segundo plano (ver
 *    KmlStopImportWorker): con archivos grandes, una llamada de geocoding por punto de
 *    forma secuencial puede tardar varios minutos, y una sola petición HTTP tan larga es
 *    frágil (una red local, Docker o proxy puede cortarla en silencio sin avisar al
 *    navegador) — el cliente solo pide "arranca este import" y consulta el progreso aparte.
 * 2) importRouteAndMatch: el Placemark-LineString del KML (el más largo si hay varios) se
 *    guarda como shape de UN pattern ya existente, emparejando paradas por cercanía. No
 *    hace llamadas de geocoding (solo matemática local contra paradas ya existentes), así
 *    que es rápido incluso con muchas paradas — se mantiene síncrono.
 * 3) importRoutesFromKml: variante para un KML con VARIAS rutas — crea una ruta + sentido
 *    nuevo por cada LineString del KML (usando el nombre del Placemark), en vez de
 *    limitarse al sentido que el usuario ya tenga abierto. Tampoco geocodifica, síncrono.
 */
@Service
public class KmlImportService {

    // Paleta simple para que las rutas creadas en lote no salgan todas del mismo color —
    // el usuario puede cambiarlo después desde "Editar ruta" igual que cualquier otra.
    private static final String[] ROUTE_COLOR_PALETTE = {
        "1E88E5", "E53935", "43A047", "FB8C00", "8E24AA", "00897B", "5D4037", "3949AB",
    };

    private final StopRepository stopRepository;
    private final FeedVersionRepository feedVersionRepository;
    private final GtfsIdGenerator idGenerator;
    private final AgencyRepository agencyRepository;
    private final RouteRepository routeRepository;
    private final RoutePatternRepository routePatternRepository;
    private final PatternStopRepository patternStopRepository;
    private final ShapePointRepository shapePointRepository;
    private final TripRepository tripRepository;
    private final KmlStopImportJobRepository kmlStopImportJobRepository;
    private final KmlStopImportWorker kmlStopImportWorker;
    private final RoutingProvider routingProvider;

    public KmlImportService(StopRepository stopRepository, FeedVersionRepository feedVersionRepository,
            GtfsIdGenerator idGenerator, AgencyRepository agencyRepository,
            RouteRepository routeRepository, RoutePatternRepository routePatternRepository,
            PatternStopRepository patternStopRepository, ShapePointRepository shapePointRepository,
            TripRepository tripRepository, KmlStopImportJobRepository kmlStopImportJobRepository,
            KmlStopImportWorker kmlStopImportWorker, RoutingProvider routingProvider) {
        this.stopRepository = stopRepository;
        this.feedVersionRepository = feedVersionRepository;
        this.idGenerator = idGenerator;
        this.agencyRepository = agencyRepository;
        this.routeRepository = routeRepository;
        this.routePatternRepository = routePatternRepository;
        this.patternStopRepository = patternStopRepository;
        this.shapePointRepository = shapePointRepository;
        this.tripRepository = tripRepository;
        this.kmlStopImportJobRepository = kmlStopImportJobRepository;
        this.kmlStopImportWorker = kmlStopImportWorker;
        this.routingProvider = routingProvider;
    }

    /** Parsea el KML (rápido) y arranca el trabajo pesado en segundo plano; devuelve el jobId para hacer polling. */
    public KmlStopImportJob startStopsImport(UUID feedVersionId, InputStream kml) throws Exception {
        FeedVersion feedVersion = feedVersionRepository.findById(feedVersionId)
                .orElseThrow(() -> new NoSuchElementException("feed_version no encontrado: " + feedVersionId));

        List<KmlParser.KmlPoint> points = KmlParser.parsePoints(kml);

        KmlStopImportJob job = KmlStopImportJob.builder()
                .feedVersion(feedVersion)
                .status(KmlStopImportJob.Status.RUNNING.name())
                .totalPoints(points.size())
                .processedCount(0)
                .geocodedCount(0)
                .startedAt(OffsetDateTime.now())
                .build();
        job = kmlStopImportJobRepository.save(job);

        kmlStopImportWorker.run(job.getId(), feedVersion, points);
        return job;
    }

    public KmlStopImportJob getStopsImportJob(UUID jobId) {
        return kmlStopImportJobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("job de import no encontrado: " + jobId));
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
            RoutePattern pattern, UUID feedVersionId, KmlParser.KmlLine kmlLine, double matchRadiusMeters) {
        UUID routePatternId = pattern.getId();

        // El trazo del KML rara vez cae exacto sobre la calle (se dibujó a mano en
        // Google Earth/QGIS, o viene de un GPS impreciso) — antes se guardaba tal
        // cual, así que el shape final se veía "flotando" cerca de la calle en vez
        // de sobre ella. Se pega a la red vial real con el mismo motor OSRM que ya
        // usa "Dibujar"/"Agregar paradas"; si falla o no hay proveedor configurado,
        // se sigue con el trazo del KML sin pegar (nunca rompe el import).
        boolean matchedToRoadNetwork = false;
        KmlParser.KmlLine line = kmlLine;
        List<RoutingProvider.Waypoint> waypoints = new ArrayList<>();
        for (int i = 0; i < kmlLine.lats().length; i++) {
            waypoints.add(new RoutingProvider.Waypoint(kmlLine.lats()[i], kmlLine.lons()[i]));
        }
        RoutingProvider.RouteGeometry matched = routingProvider.match(waypoints);
        if (matched.routed() && matched.pointsLatLon().size() >= 2) {
            double[] matchedLats = new double[matched.pointsLatLon().size()];
            double[] matchedLons = new double[matched.pointsLatLon().size()];
            for (int i = 0; i < matched.pointsLatLon().size(); i++) {
                matchedLats[i] = matched.pointsLatLon().get(i)[0];
                matchedLons[i] = matched.pointsLatLon().get(i)[1];
            }
            line = new KmlParser.KmlLine(kmlLine.name(), matchedLats, matchedLons);
            matchedToRoadNetwork = true;
        }

        double[] cumulative = GeoUtils.cumulativeDistancesMeters(line.lats(), line.lons());

        record Candidate(Stop stop, double alongMeters, double distanceMeters) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (Stop stop : stopRepository.findByFeedVersionId(feedVersionId)) {
            GeoUtils.Projection proj = GeoUtils.projectPointOntoPolyline(
                    stop.getStopLat(), stop.getStopLon(), line.lats(), line.lons());
            // Solo del lado derecho del sentido de avance del trazo (sección "Importar
            // rutas desde KML") — con tránsito por la derecha, un camión únicamente
            // recoge de ese lado; sin este filtro, una avenida de dos sentidos mezclaba
            // paradas de IDA y REGRESO que estaban a metros de diferencia entre sí.
            if (proj.distanceMeters() <= matchRadiusMeters && proj.rightOfTravel()) {
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
        return new PatternImportResult(
                shapePoints.size(), matchedSummaries.size(), matchRadiusMeters, matchedSummaries, matchedToRoadNetwork);
    }

    public record MatchedStopSummary(String id, String name, double distanceMeters) {
    }

    public record PatternImportResult(
            int shapePointCount, int matchedStopCount, double matchRadiusMeters, List<MatchedStopSummary> matchedStops,
            boolean matchedToRoadNetwork) {
    }

    public record RouteImportResult(
            String routeId, String routeName, String patternId, PatternImportResult pattern) {
    }

    public record BulkRoutesImportResult(int routeCount, List<RouteImportResult> routes) {
    }
}
