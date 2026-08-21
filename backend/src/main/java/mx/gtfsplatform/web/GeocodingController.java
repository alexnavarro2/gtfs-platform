package mx.gtfsplatform.web;

import mx.gtfsplatform.geocoding.GeocodingProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Sugerencia de nombre de parada por intersección más cercana (sección 6 del prompt). */
@RestController
@RequestMapping("/api/v1/geocoding")
public class GeocodingController {

    private final GeocodingProvider geocodingProvider;

    public GeocodingController(GeocodingProvider geocodingProvider) {
        this.geocodingProvider = geocodingProvider;
    }

    @GetMapping("/suggest-stop-name")
    public Map<String, String> suggestStopName(@RequestParam double lat, @RequestParam double lon) {
        return geocodingProvider.suggestStopName(lat, lon)
                .map(name -> Map.of("suggestedName", name))
                .orElse(Map.of());
    }
}
