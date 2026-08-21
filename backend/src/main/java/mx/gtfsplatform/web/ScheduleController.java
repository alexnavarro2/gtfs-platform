package mx.gtfsplatform.web;

import mx.gtfsplatform.domain.Trip;
import mx.gtfsplatform.schedule.ScheduleGenerationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patterns/{patternId}/schedule")
public class ScheduleController {

    private final ScheduleGenerationService scheduleGenerationService;

    public ScheduleController(ScheduleGenerationService scheduleGenerationService) {
        this.scheduleGenerationService = scheduleGenerationService;
    }

    @PostMapping("/explicit")
    public List<Trip> generateExplicit(@PathVariable UUID patternId,
                                        @RequestBody ScheduleGenerationService.ExplicitScheduleRequest request) {
        return scheduleGenerationService.generateExplicitTrips(patternId, request);
    }

    @PostMapping("/frequency")
    public Trip generateFrequency(@PathVariable UUID patternId,
                                   @RequestBody ScheduleGenerationService.FrequencyScheduleRequest request) {
        return scheduleGenerationService.generateFrequencyTrip(patternId, request);
    }
}
