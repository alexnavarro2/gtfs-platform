package mx.gtfsplatform.web;

import java.util.NoSuchElementException;
import java.util.UUID;
import mx.gtfsplatform.gtfs.kml.KmlImportService;
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

    @PostMapping("/api/v1/feed-versions/{feedVersionId}/stops/import-kml")
    public KmlImportService.StopsImportResult importStops(
            @PathVariable UUID feedVersionId, @RequestParam("file") MultipartFile file) {
        try {
            return kmlImportService.importStops(feedVersionId, file.getInputStream());
        } catch (NoSuchElementException e) {
            throw new ResourceNotFoundException(e.getMessage());
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo leer el KML: " + e.getMessage());
        }
    }

    @PostMapping("/api/v1/patterns/{patternId}/import-kml")
    public KmlImportService.PatternImportResult importRoute(
            @PathVariable UUID patternId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "matchRadiusMeters", defaultValue = "40") double matchRadiusMeters) {
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
            @RequestParam(value = "matchRadiusMeters", defaultValue = "40") double matchRadiusMeters) {
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
