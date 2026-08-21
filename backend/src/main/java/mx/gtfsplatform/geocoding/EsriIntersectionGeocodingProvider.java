package mx.gtfsplatform.geocoding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Mismo mecanismo que usa el editor de Conveyal (datatools-ui,
 * lib/editor/util/map.js#constructStop + lib/scenario-editor/utils/reverse.js#reverseEsri):
 * reverse-geocoding contra el ArcGIS World Geocoding Service de Esri con
 * returnIntersection=true, que devuelve directamente "Calle A &amp; Avenida B" ya
 * formateado cuando el punto no cae sobre una dirección exacta — Esri hace todo el
 * trabajo de encontrar la intersección más cercana, a diferencia de
 * {@link OverpassGeocodingProvider} que lo calcula localmente a partir de geometría
 * cruda de OSM. No requiere API key para uso básico no persistido (sin forStorage=true),
 * igual que lo usa Conveyal; opcionalmente se puede configurar un token propio
 * (gtfsplatform.geocoding.esri-api-key) para mayor cuota/confiabilidad en producción.
 */
public class EsriIntersectionGeocodingProvider implements GeocodingProvider {

    private static final Logger log = LoggerFactory.getLogger(EsriIntersectionGeocodingProvider.class);
    private static final String REVERSE_GEOCODE_URL =
            "https://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer/reverseGeocode";

    private final String apiKey;
    private final Duration timeout;
    private final ObjectMapper mapper = new ObjectMapper();

    public EsriIntersectionGeocodingProvider(String apiKey, int timeoutSeconds) {
        this.apiKey = apiKey;
        this.timeout = Duration.ofSeconds(Math.max(3, timeoutSeconds));
    }

    @Override
    public Optional<String> suggestStopName(double lat, double lon) {
        try (HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build()) {
            StringBuilder url = new StringBuilder(REVERSE_GEOCODE_URL)
                    .append("?location=").append(URLEncoder.encode(lon + "," + lat, StandardCharsets.UTF_8))
                    .append("&returnIntersection=true")
                    .append("&f=pjson");
            if (apiKey != null && !apiKey.isBlank()) {
                url.append("&token=").append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                    .timeout(timeout)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("ArcGIS reverseGeocode respondió {} para ({}, {})", response.statusCode(), lat, lon);
                return Optional.empty();
            }

            JsonNode root = mapper.readTree(response.body());
            if (root.has("error")) {
                log.warn("ArcGIS reverseGeocode devolvió error para ({}, {}): {}", lat, lon, root.path("error"));
                return Optional.empty();
            }

            String address = root.path("address").path("Address").asText(null);
            if (address == null || address.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(address);
        } catch (Exception e) {
            log.warn("No se pudo obtener sugerencia de nombre de parada (Esri) cerca de ({}, {}): {}", lat, lon, e.toString());
            return Optional.empty();
        }
    }
}
