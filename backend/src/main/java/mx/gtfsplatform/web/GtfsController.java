package mx.gtfsplatform.web;

import mx.gtfsplatform.domain.FeedVersion;
import mx.gtfsplatform.domain.FeedVersionStatus;
import mx.gtfsplatform.gtfs.GtfsEngine;
import mx.gtfsplatform.gtfs.export.GtfsExportServiceImpl;
import mx.gtfsplatform.repository.FeedVersionRepository;
import mx.gtfsplatform.validation.ValidationOrchestrator;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Sección 27 (exportar) y 24-26 (validar) del prompt. */
@RestController
@RequestMapping("/api/v1/feed-versions/{feedVersionId}")
public class GtfsController {

    private final GtfsExportServiceImpl exportService;
    private final ValidationOrchestrator validationOrchestrator;
    private final FeedVersionRepository feedVersionRepository;

    public GtfsController(GtfsExportServiceImpl exportService, ValidationOrchestrator validationOrchestrator,
                           FeedVersionRepository feedVersionRepository) {
        this.exportService = exportService;
        this.validationOrchestrator = validationOrchestrator;
        this.feedVersionRepository = feedVersionRepository;
    }

    @PostMapping("/export")
    public Map<String, Object> export(@PathVariable UUID feedVersionId) {
        GtfsEngine.ExportResult result = exportService.export(feedVersionId);
        return Map.of(
                "sha256", result.sha256(),
                "sizeBytes", result.sizeBytes(),
                "generatedAt", result.generatedAt().toString()
        );
    }

    @GetMapping("/export/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable UUID feedVersionId) {
        GtfsEngine.ExportResult result = exportService.export(feedVersionId);
        FileSystemResource resource = new FileSystemResource(result.zipPath());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"gtfs.zip\"")
                .header("X-Checksum-SHA256", result.sha256())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(result.sizeBytes())
                .body(resource);
    }

    @PostMapping("/validate")
    public ValidationOrchestrator.Summary validate(@PathVariable UUID feedVersionId,
                                                     @RequestParam(defaultValue = "true") boolean official) {
        ValidationOrchestrator.Summary summary = validationOrchestrator.validate(feedVersionId, official);
        FeedVersion feedVersion = feedVersionRepository.findById(feedVersionId)
                .orElseThrow(() -> new NoSuchElementException("feed_version no encontrado: " + feedVersionId));
        feedVersion.setStatus(summary.errors() == 0 ? FeedVersionStatus.VALID : FeedVersionStatus.DRAFT);
        feedVersion.setUpdatedAt(OffsetDateTime.now());
        feedVersionRepository.save(feedVersion);
        return summary;
    }

    @PostMapping("/publish")
    public FeedVersion publish(@PathVariable UUID feedVersionId) {
        FeedVersion feedVersion = feedVersionRepository.findById(feedVersionId)
                .orElseThrow(() -> new NoSuchElementException("feed_version no encontrado: " + feedVersionId));
        if (feedVersion.getStatus() != FeedVersionStatus.VALID) {
            throw new IllegalStateException(
                    "Solo se puede publicar una versión VALID (0 errores). Estado actual: " + feedVersion.getStatus());
        }
        feedVersion.setStatus(FeedVersionStatus.PUBLISHED);
        feedVersion.setUpdatedAt(OffsetDateTime.now());
        return feedVersionRepository.save(feedVersion);
    }
}
