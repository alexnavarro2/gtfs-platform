package mx.gtfsplatform.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
}
