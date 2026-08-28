package mx.gtfsplatform.gtfs;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Asigna el gtfs_id (business key expuesta en el .txt) UNA sola vez, en el momento en
 * que la entidad se crea. A partir de ahí el valor se guarda en columna y el exportador
 * lo usa tal cual — nunca se recalcula en cada exportación (sección 32 del prompt: los
 * IDs deben permanecer persistentes entre versiones). El contador secuencial de aquí
 * solo determina el valor inicial, no algo que se repita en cada export.
 */
@Service
public class GtfsIdGenerator {

    private final JdbcTemplate jdbcTemplate;

    public GtfsIdGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String next(String table, String prefix, UUID scopeId, String scopeColumn) {
        return next(table, "gtfs_id", prefix, scopeId, scopeColumn);
    }

    public String next(String table, String idColumn, String prefix, UUID scopeId, String scopeColumn) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + scopeColumn + " = ?",
                Integer.class, scopeId);
        int n = (count == null ? 0 : count) + 1;
        String candidate = prefix + "_" + String.format("%05d", n);
        // COUNT(*) > 0 en vez de "SELECT EXISTS(...)": ese es válido en Postgres
        // (devuelve un booleano) pero no es sintaxis T-SQL válida en SQL Server —
        // COUNT funciona igual en ambos motores.
        while (existsCount(table, scopeColumn, scopeId, idColumn, candidate) > 0) {
            n++;
            candidate = prefix + "_" + String.format("%05d", n);
        }
        return candidate;
    }

    private int existsCount(String table, String scopeColumn, UUID scopeId, String idColumn, String candidate) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + scopeColumn + " = ? AND " + idColumn + " = ?",
                Integer.class, scopeId, candidate);
        return count == null ? 0 : count;
    }
}
