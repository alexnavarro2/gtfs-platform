package mx.gtfsplatform.web;

import java.util.NoSuchElementException;
import java.util.UUID;
import mx.gtfsplatform.domain.KmlStopImportJob;
import mx.gtfsplatform.gtfs.kml.KmlImportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class KmlImportController {

    private final KmlImportService kmlImportService;

    public KmlImportController(KmlImportService kmlImportService) {
        this.kmlImportService = kmlImportService;
    }

    public record StopImportJobStatus(
            String jobId, String status, int totalPoints, int processedCount, int geocodedCount,
            String errorMessage, Double minLat, Double maxLat, Double minLon, Double maxLon) {
        static StopImportJobStatus of(KmlStopImportJob job) {
            return new StopImportJobStatus(job.getId().toString(), job.getStatus(), job.getTotalPoints(),
                    job.getProcessedCount(), job.getGeocodedCount(), job.getErrorMessage(),
                    job.getMinLat(), job.getMaxLat(), job.getMinLon(), job.getMaxLon());
        }
    }

    // Solo arranca el import y devuelve el jobId — el trabajo pesado (geocoding punto por
    // punto) corre en segundo plano; el cliente hace polling con GET .../stop-import-jobs/{jobId}.
    @PostMapping("/api/v1/feed-versions/{feedVersionId}/stops/import-kml")
    public StopImportJobStatus importStops(
            @PathVariable UUID feedVersionId, @RequestParam("file") MultipartFile file) {
        try {
            KmlStopImportJob job = kmlImportService.startStopsImport(feedVersionId, file.getInputStream());
            return StopImportJobStatus.of(job);
        } catch (NoSuchElementException e) {
            throw new ResourceNotFoundException(e.getMessage());
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo leer el KML: " + e.getMessage());
        }
    }

    @GetMapping("/api/v1/stop-import-jobs/{jobId}")
    public StopImportJobStatus getStopsImportJob(@PathVariable UUID jobId) {
        try {
            return StopImportJobStatus.of(kmlImportService.getStopsImportJob(jobId));
        } catch (NoSuchElementException e) {
            throw new ResourceNotFoundException(e.getMessage());
        }
    }

    @PostMapping("/api/v1/patterns/{patternId}/import-kml")
    public KmlImportService.PatternImportResult importRoute(
            @PathVariable UUID patternId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "matchRadiusMeters", defaultValue = "20") double matchRadiusMeters) {
        try {
            return kmlImportService.importRouteAndMatch(patternId, file.getInputStream(), matchRadiusMeters);
        } catch (NoSuchElementException e) {
            throw new ResourceNotFoundException(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo leer el KML: " + e.getMessage());
        }
    }

    // KML con varias rutas (una LineString por Placemark): crea una ruta + sentido nuevo
    // por cada una, en vez de limitarse al sentido que el usuario ya tenga abierto.
    @PostMapping("/api/v1/feed-versions/{feedVersionId}/routes/import-kml")
    public KmlImportService.BulkRoutesImportResult importRoutes(
            @PathVariable UUID feedVersionId,
            @RequestParam("agencyId") UUID agencyId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "matchRadiusMeters", defaultValue = "20") double matchRadiusMeters) {
        try {
            return kmlImportService.importRoutesFromKml(feedVersionId, agencyId, file.getInputStream(), matchRadiusMeters);
        } catch (NoSuchElementException e) {
            throw new ResourceNotFoundException(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo leer el KML: " + e.getMessage());
        }
    }
}
