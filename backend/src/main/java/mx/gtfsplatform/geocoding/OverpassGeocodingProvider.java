package mx.gtfsplatform.geocoding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.gtfsplatform.geo.GeoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *
 * El endpoint público overpass-api.de resultó ser intermitente en pruebas (conexiones
 * rechazadas/timeouts esporádicos, ver docs/ARCHITECTURE-PLAN.md sección I) — cuando
 * el usuario deja la URL por defecto sin configurar la suya propia, se rota entre
 * varios espejos públicos conocidos de Overpass en vez de depender de uno solo.
 */
public class OverpassGeocodingProvider implements GeocodingProvider {

    private static final Logger log = LoggerFactory.getLogger(OverpassGeocodingProvider.class);
    private static final String DEFAULT_URL = "https://overpass-api.de/api/interpreter";
    private static final List<String> PUBLIC_MIRRORS = List.of(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.openstreetmap.ru/api/interpreter"
    );

    private final List<String> candidateUrls;
    private final double searchRadiusMeters;
    private final Duration timeout;
    private final ObjectMapper mapper = new ObjectMapper();

    public OverpassGeocodingProvider(String overpassUrl, double searchRadiusMeters, int timeoutSeconds) {
        // Si el usuario configuró su propia URL (self-hosted), se respeta tal cual —
        // solo rotamos entre espejos públicos cuando se usa el valor por defecto.
        this.candidateUrls = DEFAULT_URL.equals(overpassUrl) ? PUBLIC_MIRRORS : List.of(overpassUrl);
        this.searchRadiusMeters = searchRadiusMeters;
        this.timeout = Duration.ofSeconds(Math.max(3, timeoutSeconds));
    }

    @Override
    public Optional<String> suggestStopName(double lat, double lon) {
        // Dos pasadas acotadas (radio base, luego uno más amplio) por cada espejo
        // disponible, para no dejar la creación de la parada esperando demasiado si
        // el servicio público está degradado.
        for (double radius : new double[]{searchRadiusMeters, searchRadiusMeters * 4}) {
            for (String url : candidateUrls) {
                QueryOutcome outcome = query(url, lat, lon, radius);
                if (outcome.name().isPresent()) {
                    return outcome.name();
                }
            }
        }
        return Optional.empty();
    }

    private record QueryOutcome(Optional<String> name, boolean reachable) {
    }

    private QueryOutcome query(String url, double lat, double lon, double radiusMeters) {
        try (HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .version(HttpClient.Version.HTTP_1_1)
                .build()) {
            String queryText = "[out:json][timeout:5];way(around:" + radiusMeters + "," + lat + "," + lon
                    + ")[highway][name];out tags geom;";
            String body = "data=" + java.net.URLEncoder.encode(queryText, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("{} respondió {} al buscar intersección cerca de ({}, {})", url, response.statusCode(), lat, lon);
                return new QueryOutcome(Optional.empty(), true);
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
                return new QueryOutcome(Optional.empty(), true);
            }
            if (names.size() == 1) {
                return new QueryOutcome(Optional.of(names.get(0)), true);
            }
            return new QueryOutcome(Optional.of(names.get(0) + " & " + names.get(1)), true);
        } catch (Exception e) {
            log.warn("No se pudo contactar {} buscando intersección cerca de ({}, {}): {}", url, lat, lon, e.toString());
            return new QueryOutcome(Optional.empty(), false);
        }
    }

    private record NamedWay(String name, double distanceMeters) {
    }
}
