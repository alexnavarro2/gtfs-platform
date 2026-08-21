package mx.gtfsplatform.validation;

import mx.gtfsplatform.domain.FeedVersion;
import mx.gtfsplatform.domain.ValidationNotice;
import mx.gtfsplatform.domain.ValidationRun;
import mx.gtfsplatform.gtfs.GtfsEngine;
import mx.gtfsplatform.gtfs.export.GtfsExportServiceImpl;
import mx.gtfsplatform.repository.FeedVersionRepository;
import mx.gtfsplatform.repository.ValidationNoticeRepository;
import mx.gtfsplatform.repository.ValidationRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Sección 24-26 del prompt: corre reglas internas + validador oficial, persiste el
 * resultado como un ValidationRun con sus ValidationNotice, y aplica la política de
 * publicación (ERROR bloquea, WARNING permite con confirmación).
 */
@Service
public class ValidationOrchestrator {

    private final InternalRuleEngine internalRuleEngine;
    private final MobilityDataValidatorService mobilityDataValidatorService;
    private final GtfsExportServiceImpl exportService;
    private final FeedVersionRepository feedVersionRepository;
    private final ValidationRunRepository validationRunRepository;
    private final ValidationNoticeRepository validationNoticeRepository;

    public ValidationOrchestrator(InternalRuleEngine internalRuleEngine,
                                   MobilityDataValidatorService mobilityDataValidatorService,
                                   GtfsExportServiceImpl exportService,
                                   FeedVersionRepository feedVersionRepository,
                                   ValidationRunRepository validationRunRepository,
                                   ValidationNoticeRepository validationNoticeRepository) {
        this.internalRuleEngine = internalRuleEngine;
        this.mobilityDataValidatorService = mobilityDataValidatorService;
        this.exportService = exportService;
        this.feedVersionRepository = feedVersionRepository;
        this.validationRunRepository = validationRunRepository;
        this.validationNoticeRepository = validationNoticeRepository;
    }

    public record Summary(UUID validationRunId, long errors, long warnings, long infos,
                           boolean publishable, List<ValidationNotice> notices) {
    }

    @Transactional
    public Summary validate(UUID feedVersionId, boolean runOfficialValidator) {
        FeedVersion feedVersion = feedVersionRepository.findById(feedVersionId)
                .orElseThrow(() -> new NoSuchElementException("feed_version no encontrado: " + feedVersionId));

        ValidationRun run = ValidationRun.builder()
                .feedVersion(feedVersion)
                .source(runOfficialValidator ? ValidationRun.Source.MOBILITYDATA : ValidationRun.Source.INTERNAL)
                .status(ValidationRun.Status.RUNNING)
                .startedAt(Instant.now())
                .build();
        run = validationRunRepository.save(run);

        List<ValidationNotice> notices = internalRuleEngine.run(feedVersionId);

        if (runOfficialValidator) {
            try {
                GtfsEngine.ExportResult exportResult = exportService.export(feedVersionId);
                notices.addAll(mobilityDataValidatorService.validate(exportResult.zipPath()));
            } catch (Exception e) {
                notices.add(ValidationNotice.builder()
                        .severity(ValidationNotice.Severity.WARNING)
                        .category(ValidationNotice.Category.LOCAL_QUALITY_RULE)
                        .code("official_validation_failed")
                        .title("No se pudo generar/validar el GTFS con el validador oficial: " + e.getMessage())
                        .build());
            }
        }

        for (ValidationNotice n : notices) {
            n.setValidationRun(run);
        }
        validationNoticeRepository.saveAll(notices);

        run.setStatus(ValidationRun.Status.DONE);
        run.setFinishedAt(Instant.now());
        validationRunRepository.save(run);

        long errors = notices.stream().filter(n -> n.getSeverity() == ValidationNotice.Severity.ERROR).count();
        long warnings = notices.stream().filter(n -> n.getSeverity() == ValidationNotice.Severity.WARNING).count();
        long infos = notices.stream().filter(n -> n.getSeverity() == ValidationNotice.Severity.INFO).count();

        // Política de publicación (sección 26): ERROR bloquea, WARNING permite con confirmación explícita del usuario.
        return new Summary(run.getId(), errors, warnings, infos, errors == 0, notices);
    }
}
