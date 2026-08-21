package mx.gtfsplatform.gtfs.export;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Escribe una tabla GTFS (.txt) en UTF-8, RFC4180, encabezados exactos, sin BOM. */
public final class CsvTable {

    @FunctionalInterface
    public interface Body {
        void write(CSVPrinter printer) throws IOException;
    }

    private CsvTable() {
    }

    public static void write(Path dir, String fileName, List<String> headers, Body body) {
        Path file = dir.resolve(fileName);
        CSVFormat format = CSVFormat.RFC4180.builder()
                .setHeader(headers.toArray(new String[0]))
                .build();
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, format)) {
            body.write(printer);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo escribir " + fileName, e);
        }
    }
}
