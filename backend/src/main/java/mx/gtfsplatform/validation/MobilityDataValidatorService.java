package mx.gtfsplatform.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.gtfsplatform.domain.ValidationNotice;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Ejecuta el validador canónico de MobilityData como proceso externo (sección 25/53
 * del prompt) y traduce report.json a ValidationNotice. Si el jar no está disponible
 * (p. ej. no se pudo descargar en el build, ver backend/Dockerfile), se degrada
 * devolviendo una lista vacía con una notice INFO explicando la ausencia, en vez de
 * romper el flujo de validación interno.
 */
@Service
public class MobilityDataValidatorService {

    private final String jarPath;
    private final int timeoutSeconds;
    private final ObjectMapper mapper = new ObjectMapper();

    public MobilityDataValidatorService(@Value("${gtfsplatform.validator.jar-path}") String jarPath,
                                         @Value("${gtfsplatform.validator.timeout-seconds}") int timeoutSeconds) {
        this.jarPath = jarPath;
        this.timeoutSeconds = timeoutSeconds;
    }

    public List<ValidationNotice> validate(Path gtfsZip) {
        List<ValidationNotice> notices = new ArrayList<>();
        Path jar = Path.of(jarPath);
        if (!Files.exists(jar)) {
            notices.add(ValidationNotice.builder()
                    .severity(ValidationNotice.Severity.INFO)
                    .category(ValidationNotice.Category.LOCAL_QUALITY_RULE)
                    .code("validator_unavailable")
                    .title("El validador oficial de MobilityData no está disponible en este despliegue")
                    .build());
            return notices;
        }

        try {
            Path outDir = Files.createTempDirectory("gtfs-validator-out-");
            ProcessBuilder pb = new ProcessBuilder("java", "-jar", jar.toString(),
                    "-i", gtfsZip.toString(), "-o", outDir.toString(), "--country_code", "mx")
                    .redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                notices.add(errorNotice("validator_timeout", "El validador oficial excedió el tiempo límite"));
                return notices;
            }

            Path report = outDir.resolve("report.json");
            if (!Files.exists(report)) {
                notices.add(errorNotice("validator_no_report",
                        "El validador oficial no generó report.json (código de salida " + process.exitValue() + ")"));
                return notices;
            }

            JsonNode root = mapper.readTree(report.toFile());
            JsonNode noticeGroups = root.path("notices");
            for (JsonNode group : noticeGroups) {
                String code = group.path("code").asText("unknown");
                String severity = group.path("severity").asText("WARNING");
                JsonNode sampleNotices = group.path("sampleNotices");
                int total = group.path("totalNotices").asInt(sampleNotices.size());
                notices.add(ValidationNotice.builder()
                        .severity(mapSeverity(severity))
                        .category(ValidationNotice.Category.GTFS_SPEC)
                        .code(code)
                        .title(code + " (" + total + " ocurrencia" + (total == 1 ? "" : "s") + ")")
                        .description(sampleNotices.isEmpty() ? null : sampleNotices.get(0).toString())
                        .build());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            notices.add(errorNotice("validator_failed", "No se pudo ejecutar el validador oficial: " + e.getMessage()));
        }
        return notices;
    }

    private ValidationNotice errorNotice(String code, String title) {
        return ValidationNotice.builder()
                .severity(ValidationNotice.Severity.WARNING)
                .category(ValidationNotice.Category.LOCAL_QUALITY_RULE)
                .code(code)
                .title(title)
                .build();
    }

    private ValidationNotice.Severity mapSeverity(String mobilityDataSeverity) {
        return switch (mobilityDataSeverity.toUpperCase()) {
            case "ERROR" -> ValidationNotice.Severity.ERROR;
            case "WARNING" -> ValidationNotice.Severity.WARNING;
            default -> ValidationNotice.Severity.INFO;
        };
    }
}
