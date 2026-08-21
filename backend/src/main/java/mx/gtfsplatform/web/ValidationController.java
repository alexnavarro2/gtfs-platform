package mx.gtfsplatform.web;

import mx.gtfsplatform.domain.ValidationNotice;
import mx.gtfsplatform.domain.ValidationRun;
import mx.gtfsplatform.repository.ValidationNoticeRepository;
import mx.gtfsplatform.repository.ValidationRunRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class ValidationController {

    private final ValidationRunRepository validationRunRepository;
    private final ValidationNoticeRepository validationNoticeRepository;

    public ValidationController(ValidationRunRepository validationRunRepository,
                                 ValidationNoticeRepository validationNoticeRepository) {
        this.validationRunRepository = validationRunRepository;
        this.validationNoticeRepository = validationNoticeRepository;
    }

    @GetMapping("/api/v1/feed-versions/{feedVersionId}/validation-runs")
    public List<ValidationRun> runs(@PathVariable UUID feedVersionId) {
        return validationRunRepository.findByFeedVersionIdOrderByStartedAtDesc(feedVersionId);
    }

    @GetMapping("/api/v1/validation-runs/{runId}/notices")
    public List<ValidationNotice> notices(@PathVariable UUID runId) {
        return validationNoticeRepository.findByValidationRunId(runId);
    }
}
