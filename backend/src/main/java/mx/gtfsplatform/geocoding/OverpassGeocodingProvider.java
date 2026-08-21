package mx.gtfsplatform.geocoding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.gtfsplatform.geo.GeoUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Sugiere "Calle A &amp; Avenida B" a partir de las vías con nombre más cercanas al
 * punto, igual que el editor de Conveyal Data Tools. Usa la API pública de Overpass
 * por defecto (una sola consulta acotada por clic en "nueva parada", no un
 * autocomplete continuo) — configurable vía gtfsplatform.geocoding.overpass-url hacia
 * una instancia propia en producción (ver docs/ARCHITECTURE-PLAN.md, sección F).
 * Cualquier fallo (timeout, sin red, sin resultados) se degrada a "sin sugerencia":
 * nunca bloquea la creación de la parada.
 */
public class OverpassGeocodingProvider implements GeocodingProvider {

    private final String overpassUrl;
    private final double searchRadiusMeters;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public OverpassGeocodingProvider(String overpassUrl, double searchRadiusMeters, int timeoutSeconds) {
        this.overpassUrl = overpassUrl;
        this.searchRadiusMeters = searchRadiusMeters;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build();
    }

    @Override
    public Optional<String> suggestStopName(double lat, double lon) {
        try {
            String query = "[out:json][timeout:8];way(around:" + searchRadiusMeters + "," + lat + "," + lon
                    + ")[highway][name];out tags geom;";
            String body = "data=" + java.net.URLEncoder.encode(query, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder(URI.create(overpassUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }

            JsonNode elements = mapper.readTree(response.body()).path("elements");
            List<NamedWay> ways = new ArrayList<>();
            for (JsonNode el : elements) {
                String name = el.path("tags").path("name").asText(null);
                JsonNode geometry = el.path("geometry");
                if (name == null || !geometry.isArray() || geometry.size() < 2) {
                    continue;
                }
                double[] lats = new double[geometry.size()];
                double[] lons = new double[geometry.size()];
                for (int i = 0; i < geometry.size(); i++) {
                    lats[i] = geometry.get(i).path("lat").asDouble();
                    lons[i] = geometry.get(i).path("lon").asDouble();
                }
                GeoUtils.Projection proj = GeoUtils.projectPointOntoPolyline(lat, lon, lats, lons);
                ways.add(new NamedWay(name, proj.distanceMeters()));
            }

            LinkedHashSet<String> distinctNamesNearestFirst = new LinkedHashSet<>();
            ways.stream()
                    .sorted(Comparator.comparingDouble(w -> w.distanceMeters))
                    .forEach(w -> distinctNamesNearestFirst.add(w.name));

            List<String> names = new ArrayList<>(distinctNamesNearestFirst);
            if (names.isEmpty()) {
                return Optional.empty();
            }
            if (names.size() == 1) {
                return Optional.of(names.get(0));
            }
            return Optional.of(names.get(0) + " & " + names.get(1));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private record NamedWay(String name, double distanceMeters) {
    }
}
