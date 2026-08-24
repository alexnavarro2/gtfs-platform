package mx.gtfsplatform.web;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import mx.gtfsplatform.domain.FrequencyEntry;
import mx.gtfsplatform.domain.StopTime;
import mx.gtfsplatform.domain.Trip;
import mx.gtfsplatform.gtfs.GtfsTime;
import mx.gtfsplatform.repository.FrequencyEntryRepository;
import mx.gtfsplatform.repository.StopTimeRepository;
import mx.gtfsplatform.repository.TripRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

// Sin esto no había ninguna forma de ver qué trips (viajes) ya se generaron
// para un pattern — "Horario" solo mostraba un mensaje puntual al generar
// ("N trips generados") que se perdía al recargar o cambiar de pestaña.
@RestController
public class TripController {

    private final TripRepository tripRepository;
    private final StopTimeRepository stopTimeRepository;
    private final FrequencyEntryRepository frequencyEntryRepository;

    public TripController(
            TripRepository tripRepository,
            StopTimeRepository stopTimeRepository,
            FrequencyEntryRepository frequencyEntryRepository) {
        this.tripRepository = tripRepository;
        this.stopTimeRepository = stopTimeRepository;
        this.frequencyEntryRepository = frequencyEntryRepository;
    }

    @GetMapping("/api/v1/patterns/{patternId}/trips")
    public List<TripSummary> list(@PathVariable UUID patternId) {
        List<Trip> trips = tripRepository.findByRoutePatternId(patternId);
        List<UUID> tripIds = trips.stream().map(Trip::getId).toList();

        Map<UUID, List<StopTime>> stopTimesByTrip = stopTimeRepository.findByTripIdIn(tripIds).stream()
                .collect(Collectors.groupingBy(st -> st.getTrip().getId()));
        Map<UUID, List<FrequencyEntry>> frequenciesByTrip = frequencyEntryRepository.findByTripIdIn(tripIds).stream()
                .collect(Collectors.groupingBy(f -> f.getTrip().getId()));

        return trips.stream()
                .sorted(Comparator.comparing(Trip::getGtfsId))
                .map(t -> toSummary(t, stopTimesByTrip.getOrDefault(t.getId(), List.of()),
                        frequenciesByTrip.getOrDefault(t.getId(), List.of())))
                .toList();
    }

    @DeleteMapping("/api/v1/trips/{id}")
    @Transactional
    public void delete(@PathVariable UUID id) {
        stopTimeRepository.deleteByTripId(id);
        tripRepository.deleteById(id);
    }

    private TripSummary toSummary(Trip t, List<StopTime> stopTimes, List<FrequencyEntry> frequencies) {
        String firstDeparture = stopTimes.stream()
                .filter(st -> st.getDepartureTimeSec() != null)
                .min(Comparator.comparingInt(StopTime::getStopSequence))
                .map(st -> GtfsTime.formatFromSeconds(st.getDepartureTimeSec()))
                .orElse(null);
        String lastArrival = stopTimes.stream()
                .filter(st -> st.getArrivalTimeSec() != null)
                .max(Comparator.comparingInt(StopTime::getStopSequence))
                .map(st -> GtfsTime.formatFromSeconds(st.getArrivalTimeSec()))
                .orElse(null);
        List<FrequencyWindow> windows = frequencies.stream()
                .map(f -> new FrequencyWindow(
                        GtfsTime.formatFromSeconds(f.getStartTimeSec()),
                        GtfsTime.formatFromSeconds(f.getEndTimeSec()),
                        f.getHeadwaySecs()))
                .toList();
        return new TripSummary(
                t.getId().toString(),
                t.getGtfsId(),
                t.getTripHeadsign(),
                t.getServiceCalendar() != null ? t.getServiceCalendar().getName() : null,
                Boolean.TRUE.equals(t.getFrequencyBased()),
                stopTimes.size(),
                firstDeparture,
                lastArrival,
                windows);
    }

    public record FrequencyWindow(String startTime, String endTime, Integer headwaySecs) {
    }

    public record TripSummary(
            String id,
            String gtfsId,
            String tripHeadsign,
            String serviceCalendarName,
            boolean frequencyBased,
            int stopCount,
            String firstDeparture,
            String lastArrival,
            List<FrequencyWindow> frequencies) {
    }
}
