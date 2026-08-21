package mx.gtfsplatform.schedule;

import mx.gtfsplatform.domain.*;
import mx.gtfsplatform.geo.GeoUtils;
import mx.gtfsplatform.gtfs.GtfsIdGenerator;
import mx.gtfsplatform.gtfs.GtfsTime;
import mx.gtfsplatform.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Sección 16-18 del prompt: genera trips.txt/stop_times.txt/frequencies.txt a partir de
 * un RoutePattern + ServiceCalendar, usando uno de los 3 métodos de tiempos entre
 * paradas. No expone estas entidades directamente al frontend salvo por resúmenes:
 * la responsabilidad de generar tiempos vive aquí, no en el cliente (sección 62).
 */
@Service
public class ScheduleGenerationService {

    private final RoutePatternRepository routePatternRepository;
    private final PatternStopRepository patternStopRepository;
    private final ShapePointRepository shapePointRepository;
    private final ServiceCalendarRepository serviceCalendarRepository;
    private final TripRepository tripRepository;
    private final StopTimeRepository stopTimeRepository;
    private final FrequencyEntryRepository frequencyEntryRepository;
    private final GtfsIdGenerator idGenerator;

    public ScheduleGenerationService(RoutePatternRepository routePatternRepository,
                                      PatternStopRepository patternStopRepository,
                                      ShapePointRepository shapePointRepository,
                                      ServiceCalendarRepository serviceCalendarRepository,
                                      TripRepository tripRepository,
                                      StopTimeRepository stopTimeRepository,
                                      FrequencyEntryRepository frequencyEntryRepository,
                                      GtfsIdGenerator idGenerator) {
        this.routePatternRepository = routePatternRepository;
        this.patternStopRepository = patternStopRepository;
        this.shapePointRepository = shapePointRepository;
        this.serviceCalendarRepository = serviceCalendarRepository;
        this.tripRepository = tripRepository;
        this.stopTimeRepository = stopTimeRepository;
        this.frequencyEntryRepository = frequencyEntryRepository;
        this.idGenerator = idGenerator;
    }

    /** offsets[i] = segundos desde el inicio del viaje hasta la parada i; offsets[0] = 0. */
    static final class Offsets {
        final int[] seconds;
        final Double[] shapeDistTraveledMeters; // null si no hay shape dibujado aún

        Offsets(int[] seconds, Double[] shapeDistTraveledMeters) {
            this.seconds = seconds;
            this.shapeDistTraveledMeters = shapeDistTraveledMeters;
        }
    }

    Offsets computeOffsets(List<PatternStop> orderedStops, TimingMethod method,
                            List<Integer> manualSegmentSeconds, Double speedKmh, Integer totalTripTimeSec,
                            List<ShapePoint> orderedShapePoints) {
        int n = orderedStops.size();
        if (n < 2) {
            throw new IllegalArgumentException("El patrón necesita al menos 2 paradas para generar horarios");
        }

        double[] distanceMeters = new double[n];
        Double[] shapeDist = new Double[n];

        if (!orderedShapePoints.isEmpty()) {
            double[] lats = orderedShapePoints.stream().mapToDouble(ShapePoint::getShapePtLat).toArray();
            double[] lons = orderedShapePoints.stream().mapToDouble(ShapePoint::getShapePtLon).toArray();
            double[] cumulative = GeoUtils.cumulativeDistancesMeters(lats, lons);
            for (int i = 0; i < n; i++) {
                PatternStop ps = orderedStops.get(i);
                double d;
                if (ps.getDistanceAlongShape() != null) {
                    d = ps.getDistanceAlongShape();
                } else {
                    GeoUtils.Projection proj = GeoUtils.projectPointOntoPolyline(
                            ps.getStop().getStopLat(), ps.getStop().getStopLon(), lats, lons);
                    d = GeoUtils.distanceAlongPolylineMeters(proj, cumulative);
                }
                distanceMeters[i] = d;
                shapeDist[i] = d;
            }
        } else {
            distanceMeters[0] = 0;
            for (int i = 1; i < n; i++) {
                Stop a = orderedStops.get(i - 1).getStop();
                Stop b = orderedStops.get(i).getStop();
                distanceMeters[i] = distanceMeters[i - 1]
                        + GeoUtils.haversineMeters(a.getStopLat(), a.getStopLon(), b.getStopLat(), b.getStopLon());
            }
        }

        int[] offsets = new int[n];
        switch (method) {
            case MANUAL_SEGMENTS -> {
                if (manualSegmentSeconds == null || manualSegmentSeconds.size() != n - 1) {
                    throw new IllegalArgumentException("Se esperaban " + (n - 1) + " segmentos manuales, llegaron "
                            + (manualSegmentSeconds == null ? 0 : manualSegmentSeconds.size()));
                }
                offsets[0] = 0;
                for (int i = 1; i < n; i++) {
                    offsets[i] = offsets[i - 1] + manualSegmentSeconds.get(i - 1);
                }
            }
            case AVERAGE_SPEED -> {
                if (speedKmh == null || speedKmh <= 0) {
                    throw new IllegalArgumentException("speedKmh debe ser > 0");
                }
                double speedMs = speedKmh * 1000.0 / 3600.0;
                for (int i = 0; i < n; i++) {
                    offsets[i] = (int) Math.round(distanceMeters[i] / speedMs);
                }
            }
            case TOTAL_TRIP_TIME -> {
                if (totalTripTimeSec == null || totalTripTimeSec <= 0) {
                    throw new IllegalArgumentException("totalTripTimeSec debe ser > 0");
                }
                double totalDistance = distanceMeters[n - 1];
                if (totalDistance <= 0) {
                    // Sin distancia conocida (todas las paradas coinciden): reparte el tiempo en partes iguales.
                    for (int i = 0; i < n; i++) {
                        offsets[i] = (int) Math.round((double) totalTripTimeSec * i / (n - 1));
                    }
                } else {
                    for (int i = 0; i < n; i++) {
                        offsets[i] = (int) Math.round(totalTripTimeSec * (distanceMeters[i] / totalDistance));
                    }
                }
            }
        }
        return new Offsets(offsets, orderedShapePoints.isEmpty() ? new Double[n] : shapeDist);
    }

    @Transactional
    public List<Trip> generateExplicitTrips(UUID patternId, ExplicitScheduleRequest req) {
        RoutePattern pattern = routePatternRepository.findById(patternId)
                .orElseThrow(() -> new NoSuchElementException("route_pattern no encontrado: " + patternId));
        ServiceCalendar calendar = serviceCalendarRepository.findById(req.serviceCalendarId())
                .orElseThrow(() -> new NoSuchElementException("service_calendar no encontrado: " + req.serviceCalendarId()));
        List<PatternStop> orderedStops = patternStopRepository.findByRoutePatternIdOrderByStopSequenceAsc(patternId);
        List<ShapePoint> orderedShapePoints = shapePointRepository.findByRoutePatternIdOrderByShapePtSequenceAsc(patternId);

        Offsets offsets = computeOffsets(orderedStops, req.method(), req.segmentSeconds(), req.speedKmh(),
                req.totalTripTimeSec(), orderedShapePoints);

        List<Trip> created = new ArrayList<>();
        for (String departure : req.departureTimes()) {
            int baseSec = GtfsTime.parseToSeconds(departure);
            String tripGtfsId = idGenerator.next("trip", "TRIP_" + pattern.getShapeGtfsId(), patternId, "route_pattern_id");

            Trip trip = Trip.builder()
                    .routePattern(pattern)
                    .serviceCalendar(calendar)
                    .gtfsId(tripGtfsId)
                    .tripHeadsign(req.tripHeadsign())
                    .tripShortName(req.tripShortName())
                    .blockId(null)
                    .wheelchairAccessible((short) 0)
                    .bikesAllowed((short) 0)
                    .frequencyBased(false)
                    .build();
            trip = tripRepository.save(trip);

            saveStopTimes(trip, orderedStops, offsets, baseSec);
            created.add(trip);
        }
        return created;
    }

    @Transactional
    public Trip generateFrequencyTrip(UUID patternId, FrequencyScheduleRequest req) {
        RoutePattern pattern = routePatternRepository.findById(patternId)
                .orElseThrow(() -> new NoSuchElementException("route_pattern no encontrado: " + patternId));
        ServiceCalendar calendar = serviceCalendarRepository.findById(req.serviceCalendarId())
                .orElseThrow(() -> new NoSuchElementException("service_calendar no encontrado: " + req.serviceCalendarId()));
        List<PatternStop> orderedStops = patternStopRepository.findByRoutePatternIdOrderByStopSequenceAsc(patternId);
        List<ShapePoint> orderedShapePoints = shapePointRepository.findByRoutePatternIdOrderByShapePtSequenceAsc(patternId);

        Offsets offsets = computeOffsets(orderedStops, req.method(), req.segmentSeconds(), req.speedKmh(),
                req.totalTripTimeSec(), orderedShapePoints);

        if (req.windows() == null || req.windows().isEmpty()) {
            throw new IllegalArgumentException("Se necesita al menos una ventana de frecuencia");
        }

        String tripGtfsId = idGenerator.next("trip", "TRIP_" + pattern.getShapeGtfsId(), patternId, "route_pattern_id");
        Trip trip = Trip.builder()
                .routePattern(pattern)
                .serviceCalendar(calendar)
                .gtfsId(tripGtfsId)
                .tripHeadsign(req.tripHeadsign())
                .tripShortName(req.tripShortName())
                .wheelchairAccessible((short) 0)
                .bikesAllowed((short) 0)
                .frequencyBased(true)
                .build();
        trip = tripRepository.save(trip);

        int baseSec = GtfsTime.parseToSeconds(req.windows().get(0).startTime());
        saveStopTimes(trip, orderedStops, offsets, baseSec);

        for (FrequencyWindow w : req.windows()) {
            FrequencyEntry entry = FrequencyEntry.builder()
                    .trip(trip)
                    .startTimeSec(GtfsTime.parseToSeconds(w.startTime()))
                    .endTimeSec(GtfsTime.parseToSeconds(w.endTime()))
                    .headwaySecs(w.headwaySeconds())
                    .exactTimes(w.exactTimes())
                    .build();
            frequencyEntryRepository.save(entry);
        }
        return trip;
    }

    private void saveStopTimes(Trip trip, List<PatternStop> orderedStops, Offsets offsets, int baseSec) {
        for (int i = 0; i < orderedStops.size(); i++) {
            PatternStop ps = orderedStops.get(i);
            int t = baseSec + offsets.seconds[i];
            StopTime st = StopTime.builder()
                    .trip(trip)
                    .patternStop(ps)
                    .stopSequence(ps.getStopSequence())
                    .arrivalTimeSec(t)
                    .departureTimeSec(t)
                    .stopHeadsign(ps.getStopHeadsign())
                    .pickupType(ps.getDefaultPickupType())
                    .dropOffType(ps.getDefaultDropOffType())
                    .shapeDistTraveled(offsets.shapeDistTraveledMeters[i])
                    .timepoint(ps.getDefaultTimepoint())
                    .build();
            stopTimeRepository.save(st);
        }
    }

    public record ExplicitScheduleRequest(UUID serviceCalendarId, TimingMethod method,
                                           List<Integer> segmentSeconds, Double speedKmh, Integer totalTripTimeSec,
                                           List<String> departureTimes, String tripHeadsign, String tripShortName) {
    }

    public record FrequencyWindow(String startTime, String endTime, int headwaySeconds, short exactTimes) {
    }

    public record FrequencyScheduleRequest(UUID serviceCalendarId, TimingMethod method,
                                            List<Integer> segmentSeconds, Double speedKmh, Integer totalTripTimeSec,
                                            List<FrequencyWindow> windows, String tripHeadsign, String tripShortName) {
    }
}
