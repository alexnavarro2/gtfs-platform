package mx.gtfsplatform.gtfs;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Frontera de desacoplo entre el dominio y la serialización/deserialización GTFS
 * (sección 52/56 del prompt). El dominio nunca escribe .txt directamente y el resto
 * de la aplicación nunca depende de los detalles de lectura/escritura CSV.
 * No se apoya en conveyal/gtfs-lib (ver docs/ARCHITECTURE-PLAN.md, sección A) — esta es
 * una implementación propia, pero la interfaz deja espacio para enchufar otro motor
 * (p. ej. gtfs-lib como adaptador de importación alterno) sin tocar el dominio.
 */
public interface GtfsEngine {

    ExportResult exportFeed(UUID feedVersionId);

    ImportResult importFeed(UUID feedId, InputStream zipInputStream, String originalFileName);

    record ExportResult(Path zipPath, String sha256, long sizeBytes, Instant generatedAt) {
    }

    record ImportResult(UUID feedVersionId, Map<String, Integer> rowCountsByTable, List<String> warnings) {
    }
}
