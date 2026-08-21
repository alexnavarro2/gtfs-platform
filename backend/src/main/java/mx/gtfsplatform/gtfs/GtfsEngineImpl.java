package mx.gtfsplatform.gtfs;

import mx.gtfsplatform.gtfs.export.GtfsExportServiceImpl;
import mx.gtfsplatform.gtfs.importer.GtfsImportServiceImpl;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.UUID;

/** Implementación propia de GtfsEngine (ver docs/ARCHITECTURE-PLAN.md, sección A): no usa conveyal/gtfs-lib. */
@Service
public class GtfsEngineImpl implements GtfsEngine {

    private final GtfsExportServiceImpl exportService;
    private final GtfsImportServiceImpl importService;

    public GtfsEngineImpl(GtfsExportServiceImpl exportService, GtfsImportServiceImpl importService) {
        this.exportService = exportService;
        this.importService = importService;
    }

    @Override
    public ExportResult exportFeed(UUID feedVersionId) {
        return exportService.export(feedVersionId);
    }

    @Override
    public ImportResult importFeed(UUID feedId, InputStream zipInputStream, String originalFileName) {
        return importService.importFeed(feedId, zipInputStream, originalFileName);
    }
}
