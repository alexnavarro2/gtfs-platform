package mx.gtfsplatform.validation;

import mx.gtfsplatform.domain.*;
import mx.gtfsplatform.geo.GeoUtils;
import mx.gtfsplatform.repository.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reglas propias (sección 24/41/42 del prompt), etiquetadas GTFS_SPEC / GTFS_BEST_PRACTICE
 * / LOCAL_QUALITY_RULE para no confundirlas con lo que reporta el validador oficial.
 * Corren en memoria sobre el modelo de dominio (no sobre el .txt) para poder señalar la
 * entidad exacta y sus coordenadas (permite centrar el mapa al hacer clic, sección 24).
 */
@Component
public class InternalRuleEngine {

    private static final double STOP_TO_SHAPE_WARNING_METERS = 100.0;

    private final AgencyRepository agencyRepository;
    private final StopRepository stopRepository;
    private final RouteRepository routeRepository;
    private final RoutePatternRepository routePatternRepository;
    private final PatternStopRepository patternStopRepository;
    private final ShapePointRepository shapePointRepository;
    private final ServiceCalendarRepository serviceCalendarRepository;
    private final TripRepository tripRepository;
    private final StopTimeRepository stopTimeRepository;

    public InternalRuleEngine(AgencyRepository agencyRepository, StopRepository stopRepository,
                               RouteRepository routeRepository, RoutePatternRepository routePatternRepository,
                               PatternStopRepository patternStopRepository, ShapePointRepository shapePointRepository,
                               ServiceCalendarRepository serviceCalendarRepository, TripRepository tripRepository,
                               StopTimeRepository stopTimeRepository) {
        this.agencyRepository = agencyRepository;
        this.stopRepository = stopRepository;
        this.routeRepository = routeRepository;
        this.routePatternRepository = routePatternRepository;
        this.patternStopRepository = patternStopRepository;
        this.shapePointRepository = shapePointRepository;
        this.serviceCalendarRepository = serviceCalendarRepository;
        this.tripRepository = tripRepository;
        this.stopTimeRepository = stopTimeRepository;
        }

    public List<ValidationNotice> run(UUID feedVersionId) {
        List<ValidationNotice> notices = new ArrayList<>();

        List<Agency> agencies = agencyRepository.findByFeedVersionId(feedVersionId);
        if (agencies.isEmpty()) {
            notices.add(notice(ValidationNotice.Severity.ERROR, ValidationNotice.Category.GTFS_SPEC,
                    "missing_agency", "El feed no tiene ninguna agencia", "agency", null, null, null));
        }

        List<Stop> stops = stopRepository.findByFeedVersionId(feedVersionId);
        for (Stop s : stops) {
            if (s.getStopName() == null || s.getStopName().isBlank()) {
                notices.add(notice(ValidationNotice.Severity.WARNING, ValidationNotice.Category.GTFS_SPEC,
                        "stop_missing_name", "Parada sin stop_name", "stop", s.getGtfsId(),
                        s.getStopLat(), s.getStopLon()));
            }
        }

        List<ServiceCalendar> calendars = serviceCalendarRepository.findByFeedVersionId(feedVersionId);
        if (calendars.isEmpty()) {
            notices.add(notice(ValidationNotice.Severity.ERROR, ValidationNotice.Category.GTFS_SPEC,
                    "missing_calendar", "El feed no tiene ningún calendario ni excepción de servicio",
                    "service_calendar", null, null, null));
        }
        for (ServiceCalendar c : calendars) {
            if (tripRepository.findByServiceCalendarId(c.getId()).isEmpty()) {
                notices.add(notice(ValidationNotice.Severity.WARNING, ValidationNotice.Category.LOCAL_QUALITY_RULE,
                        "service_without_trips", "Servicio '" + c.getName() + "' sin ningún trip asociado",
                        "service_calendar", c.getGtfsId(), null, null));
            }
        }

        List<Route> routes = routeRepository.findByFeedVersionId(feedVersionId);
        for (Route route : routes) {
            List<RoutePattern> patterns = routePatternRepository.findByRouteId(route.getId());
            if (patterns.isEmpty()) {
                notices.add(notice(ValidationNotice.Severity.WARNING, ValidationNotice.Category.LOCAL_QUALITY_RULE,
                        "route_without_pattern", "Ruta '" + displayRoute(route) + "' sin ningún patrón/sentido definido",
                        "route", route.getGtfsId(), null, null));
                continue;
            }
            for (RoutePattern pattern : patterns) {
                validatePattern(route, pattern, notices);
            }
        }

        return notices;
    }

    private void validatePattern(Route route, RoutePattern pattern, List<ValidationNotice> notices) {
        List<PatternStop> patternStops = patternStopRepository.findByRoutePatternIdOrderByStopSequenceAsc(pattern.getId());
        List<ShapePoint> shapePoints = shapePointRepository.findByRoutePatternIdOrderByShapePtSequenceAsc(pattern.getId());

        if (shapePoints.isEmpty()) {
            notices.add(notice(ValidationNotice.Severity.WARNING, ValidationNotice.Category.GTFS_BEST_PRACTICE,
                    "pattern_without_shape", "Patrón '" + pattern.getName() + "' de la ruta '" + displayRoute(route)
                            + "' sin shape dibujado", "route_pattern", pattern.getShapeGtfsId(), null, null));
        }

        if (patternStops.size() < 2) {
            notices.add(notice(ValidationNotice.Severity.ERROR, ValidationNotice.Category.LOCAL_QUALITY_RULE,
                    "pattern_too_few_stops", "Patrón '" + pattern.getName() + "' tiene menos de 2 paradas",
                    "route_pattern", pattern.getShapeGtfsId(), null, null));
        }

        if (!shapePoints.isEmpty() && !patternStops.isEmpty()) {
            double[] lats = shapePoints.stream().mapToDouble(ShapePoint::getShapePtLat).toArray();
            double[] lons = shapePoints.stream().mapToDouble(ShapePoint::getShapePtLon).toArray();
            for (PatternStop ps : patternStops) {
                Stop stop = ps.getStop();
                GeoUtils.Projection proj = GeoUtils.projectPointOntoPolyline(stop.getStopLat(), stop.getStopLon(), lats, lons);
                if (proj.distanceMeters() > STOP_TO_SHAPE_WARNING_METERS) {
                    notices.add(notice(ValidationNotice.Severity.WARNING, ValidationNotice.Category.GTFS_BEST_PRACTICE,
                            "stop_far_from_shape",
                            String.format("Parada '%s' se encuentra a %.0f m del recorrido",
                                    stop.getStopName() != null ? stop.getStopName() : stop.getGtfsId(), proj.distanceMeters()),
                            "stop", stop.getGtfsId(), stop.getStopLat(), stop.getStopLon()));
                }
            }
        }

        List<Trip> trips = tripRepository.findByRoutePatternId(pattern.getId());
        for (Trip trip : trips) {
            List<StopTime> times = stopTimeRepository.findByTripIdOrderByStopSequenceAsc(trip.getId());
            if (times.isEmpty()) {
                notices.add(notice(ValidationNotice.Severity.ERROR, ValidationNotice.Category.GTFS_SPEC,
                        "trip_without_stop_times", "Trip '" + trip.getGtfsId() + "' sin stop_times",
                        "trip", trip.getGtfsId(), null, null));
                continue;
            }
            int previousDeparture = -1;
            double previousDist = -1;
            for (StopTime st : times) {
                if (st.getArrivalTimeSec() > st.getDepartureTimeSec()) {
                    notices.add(notice(ValidationNotice.Severity.ERROR, ValidationNotice.Category.GTFS_SPEC,
                            "arrival_after_departure",
                            "En el trip '" + trip.getGtfsId() + "' arrival_time es posterior a departure_time en la parada "
                                    + st.getStopSequence(), "stop_time", trip.getGtfsId(), null, null));
                }
                if (previousDeparture >= 0 && st.getArrivalTimeSec() < previousDeparture) {
                    notices.add(notice(ValidationNotice.Severity.ERROR, ValidationNotice.Category.GTFS_SPEC,
                            "stop_times_out_of_order",
                            "En el trip '" + trip.getGtfsId() + "' los tiempos no son crecientes en la parada "
                                    + st.getStopSequence(), "stop_time", trip.getGtfsId(), null, null));
                }
                if (st.getShapeDistTraveled() != null && previousDist >= 0 && st.getShapeDistTraveled() < previousDist) {
                    notices.add(notice(ValidationNotice.Severity.ERROR, ValidationNotice.Category.GTFS_SPEC,
                            "shape_dist_traveled_decreasing",
                            "shape_dist_traveled decrece en el trip '" + trip.getGtfsId() + "', parada "
                                    + st.getStopSequence(), "stop_time", trip.getGtfsId(), null, null));
                }
                previousDeparture = st.getDepartureTimeSec();
                if (st.getShapeDistTraveled() != null) {
                    previousDist = st.getShapeDistTraveled();
                }
            }
        }
    }

    private static String displayRoute(Route route) {
        String shortName = route.getRouteShortName();
        String longName = route.getRouteLongName();
        if (shortName != null && longName != null) {
            return shortName + " - " + longName;
        }
        return shortName != null ? shortName : (longName != null ? longName : route.getGtfsId());
    }

    private ValidationNotice notice(ValidationNotice.Severity severity, ValidationNotice.Category category,
                                     String code, String title, String entityType, String entityId,
                                     Double lat, Double lon) {
        return ValidationNotice.builder()
                .severity(severity)
                .category(category)
                .code(code)
                .title(title)
                .entityType(entityType)
                .entityId(entityId)
                .lat(lat)
                .lon(lon)
                .build();
    }
}
