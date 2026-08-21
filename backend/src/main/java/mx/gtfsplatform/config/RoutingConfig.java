package mx.gtfsplatform.config;

import mx.gtfsplatform.routing.ManualRoutingProvider;
import mx.gtfsplatform.routing.OsrmRoutingProvider;
import mx.gtfsplatform.routing.RoutingProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RoutingConfig {

    @Bean
    public RoutingProvider routingProvider(GtfsPlatformProperties properties) {
        String provider = properties.getRouting().getProvider();
        String osrmUrl = properties.getRouting().getOsrmUrl();
        if ("osrm".equalsIgnoreCase(provider) && osrmUrl != null && !osrmUrl.isBlank()) {
            return new OsrmRoutingProvider(osrmUrl);
        }
        return new ManualRoutingProvider();
    }
}
