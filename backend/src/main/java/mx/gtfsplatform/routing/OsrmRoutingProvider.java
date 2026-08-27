package mx.gtfsplatform.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementación opcional (sección 9, Modo 1) contra un servidor OSRM configurable vía
 * gtfsplatform.routing.osrm-url. No se activa por defecto (ver docs/ARCHITECTURE-PLAN.md,
 * sección J) — requiere que el usuario despliegue/configure su propio OSRM con datos
 * de la región. Si la llamada falla, se degrada a geometría no ruteada en vez de
 * romper la edición del usuario.
 */
public class OsrmRoutingProvider implements RoutingProvider {

    private static final Logger log = LoggerFactory.getLogger(OsrmRoutingProvider.class);

    // El demo público de OSRM no publica un tope oficial, pero en la práctica
    // /match (a diferencia de /route) responde "TooBig" a partir de 11
    // coordenadas por petición (confirmado a mano contra router.project-osrm.org
    // — un límite mucho más estrecho de lo que parecía razonable asumir). Una
    // línea de KML puede traer cientos de puntos, así que se parte en tramos de
    // este tamaño, cada uno solapando el último punto del tramo anterior para
    // que el resultado final quede continuo al pegarlos.
    private static final int MAX_POINTS_PER_MATCH_REQUEST = 10;
    // Radio de búsqueda (metros) que OSRM usa para "buscar" la calle más cercana
    // a cada punto del trazo — un trazo hecho a mano (Google Earth, QGIS) rara
    // vez cae exacto sobre la calle real.
    private static final int MATCH_SEARCH_RADIUS_METERS = 30;

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public OsrmRoutingProvider(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public RouteGeometry route(List<Waypoint> waypoints, RoutingProfile profile) {
        String coords = waypoints.stream()
                .map(w -> w.lon() + "," + w.lat())
                .collect(Collectors.joining(";"));
        String url = baseUrl + "/route/v1/driving/" + coords + "?overview=full&geometries=geojson";

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return new ManualRoutingProvider().route(waypoints, profile);
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode coordinates = root.at("/routes/0/geometry/coordinates");
            List<double[]> points = new ArrayList<>();
            for (JsonNode c : coordinates) {
                points.add(new double[]{c.get(1).asDouble(), c.get(0).asDouble()});
            }
            if (points.isEmpty()) {
                return new ManualRoutingProvider().route(waypoints, profile);
            }
            return new RouteGeometry(points, true, "osrm");
        } catch (Exception e) {
            return new ManualRoutingProvider().route(waypoints, profile);
        }
    }

    @Override
    public RouteGeometry match(List<Waypoint> points) {
        if (points.size() < 2) {
            return new ManualRoutingProvider().match(points);
        }
        try {
            List<double[]> matched = new ArrayList<>();
            int start = 0;
            while (start < points.size() - 1) {
                int end = Math.min(start + MAX_POINTS_PER_MATCH_REQUEST, points.size());
                List<Waypoint> chunk = points.subList(start, end);
                List<double[]> chunkMatched = matchChunk(chunk);
                if (chunkMatched == null) {
                    // Un solo tramo fallando invalida el trazo completo — mejor
                    // devolver el KML crudo entero y consistente que un resultado
                    // mitad pegado a la calle, mitad no.
                    log.warn("OSRM match falló en el tramo [{}, {}) de {} puntos totales; se usa el trazo del KML sin pegar",
                            start, end, points.size());
                    return new ManualRoutingProvider().match(points);
                }
                // El primer punto de este tramo es el mismo que el último del
                // tramo anterior (se solapan a propósito); se descarta para no
                // duplicarlo al pegar los tramos.
                List<double[]> toAppend = matched.isEmpty() ? chunkMatched : chunkMatched.subList(1, chunkMatched.size());
                matched.addAll(toAppend);
                start = end - 1;
            }
            if (matched.size() < 2) {
                return new ManualRoutingProvider().match(points);
            }
            return new RouteGeometry(matched, true, "osrm");
        } catch (Exception e) {
            log.warn("OSRM match lanzó una excepción con {} puntos de entrada; se usa el trazo del KML sin pegar", points.size(), e);
            return new ManualRoutingProvider().match(points);
        }
    }

    /** Pega un solo tramo (≤ MAX_POINTS_PER_MATCH_REQUEST puntos) a la red vial. Null si falla. */
    private List<double[]> matchChunk(List<Waypoint> chunk) throws Exception {
        String coords = chunk.stream().map(w -> w.lon() + "," + w.lat()).collect(Collectors.joining(";"));
        String radiuses = chunk.stream().map(w -> String.valueOf(MATCH_SEARCH_RADIUS_METERS)).collect(Collectors.joining(";"));
        String url = baseUrl + "/match/v1/driving/" + coords
                + "?overview=full&geometries=geojson&radiuses=" + radiuses;

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warn("OSRM match respondió HTTP {} para {} puntos: {}", response.statusCode(), chunk.size(), url);
            return null;
        }
        JsonNode root = mapper.readTree(response.body());
        if (!"Ok".equals(root.path("code").asText())) {
            log.warn("OSRM match devolvió code={} (esperado Ok) para {} puntos: {}",
                    root.path("code").asText(), chunk.size(), root.path("message").asText(""));
            return null;
        }
        // Puede haber más de un "matching" si OSRM detecta un salto sospechoso en la
        // traza (gaps=split, el default) — se pegan en orden en vez de quedarse solo
        // con el más largo, para no perder tramos reales del recorrido.
        List<double[]> points = new ArrayList<>();
        for (JsonNode matching : root.path("matchings")) {
            for (JsonNode c : matching.at("/geometry/coordinates")) {
                points.add(new double[]{c.get(1).asDouble(), c.get(0).asDouble()});
            }
        }
        return points.isEmpty() ? null : points;
    }
}
