package mx.gtfsplatform.config;

import mx.gtfsplatform.geocoding.EsriIntersectionGeocodingProvider;
import mx.gtfsplatform.geocoding.GeocodingProvider;
import mx.gtfsplatform.geocoding.NoOpGeocodingProvider;
import mx.gtfsplatform.geocoding.OverpassGeocodingProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeocodingConfig {

    @Bean
    public GeocodingProvider geocodingProvider(GtfsPlatformProperties properties) {
        GtfsPlatformProperties.Geocoding cfg = properties.getGeocoding();
        return switch (cfg.getProvider() == null ? "" : cfg.getProvider().toLowerCase()) {
            case "overpass" -> new OverpassGeocodingProvider(cfg.getOverpassUrl(), cfg.getSearchRadiusMeters(), cfg.getTimeoutSeconds());
            case "esri" -> new EsriIntersectionGeocodingProvider(cfg.getEsriApiKey(), cfg.getTimeoutSeconds());
            default -> new NoOpGeocodingProvider();
        };
    }
}
