package mx.gtfsplatform.web;

import mx.gtfsplatform.config.GtfsPlatformProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** El frontend nunca cablea el proveedor de tiles/ruteo: los lee de aquí (sección 4/54). */
@RestController
@RequestMapping("/api/v1/config")
public class PublicConfigController {

    private final GtfsPlatformProperties properties;

    public PublicConfigController(GtfsPlatformProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> config() {
        return Map.of(
                "mapTileUrl", properties.getMap().getTileUrl(),
                "mapAttribution", "© OpenStreetMap contributors",
                "routingProvider", properties.getRouting().getProvider()
        );
    }
}
