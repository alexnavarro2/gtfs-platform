package mx.gtfsplatform.config;

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
        if ("overpass".equalsIgnoreCase(cfg.getProvider())) {
            return new OverpassGeocodingProvider(cfg.getOverpassUrl(), cfg.getSearchRadiusMeters(), cfg.getTimeoutSeconds());
        }
        return new NoOpGeocodingProvider();
    }
}
