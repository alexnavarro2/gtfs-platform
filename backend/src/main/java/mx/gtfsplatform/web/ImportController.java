package mx.gtfsplatform.web;

import mx.gtfsplatform.domain.Feed;
import mx.gtfsplatform.domain.ImportJob;
import mx.gtfsplatform.gtfs.GtfsEngine;
import mx.gtfsplatform.repository.FeedRepository;
import mx.gtfsplatform.repository.FeedVersionRepository;
import mx.gtfsplatform.repository.ImportJobRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Sección 28: subir un gtfs.zip y reconstruir el modelo editable a partir de él. */
@RestController
@RequestMapping("/api/v1/feeds/{feedId}/import")
public class ImportController {

    private final GtfsEngine gtfsEngine;
    private final FeedRepository feedRepository;
    private final ImportJobRepository importJobRepository;
    private final FeedVersionRepository feedVersionRepository;

    public ImportController(GtfsEngine gtfsEngine, FeedRepository feedRepository,
                             ImportJobRepository importJobRepository, FeedVersionRepository feedVersionRepository) {
        this.gtfsEngine = gtfsEngine;
        this.feedRepository = feedRepository;
        this.importJobRepository = importJobRepository;
        this.feedVersionRepository = feedVersionRepository;
    }

    @GetMapping
    public List<ImportJob> history(@PathVariable UUID feedId) {
        return importJobRepository.findByFeedIdOrderByStartedAtDesc(feedId);
    }

    @PostMapping
    public ImportJob upload(@PathVariable UUID feedId, @RequestParam("file") MultipartFile file) {
        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() -> new NoSuchElementException("feed no encontrado: " + feedId));

        ImportJob job = ImportJob.builder()
                .feed(feed)
                .fileName(file.getOriginalFilename())
                .status(ImportJob.Status.RUNNING)
                .startedAt(Instant.now())
                .build();
        job = importJobRepository.save(job);

        try {
            GtfsEngine.ImportResult result = gtfsEngine.importFeed(feedId, file.getInputStream(), file.getOriginalFilename());
            job.setStatus(ImportJob.Status.DONE);
            job.setFinishedAt(Instant.now());
            feedVersionRepository.findById(result.feedVersionId()).ifPresent(job::setResultFeedVersion);
            job.setErrorMessage("OK: " + result.rowCountsByTable()
                    + (result.warnings().isEmpty() ? "" : " | avisos: " + result.warnings()));
        } catch (IOException | RuntimeException e) {
            job.setStatus(ImportJob.Status.FAILED);
            job.setFinishedAt(Instant.now());
            job.setErrorMessage(e.getMessage());
        }
        return importJobRepository.save(job);
    }
}
