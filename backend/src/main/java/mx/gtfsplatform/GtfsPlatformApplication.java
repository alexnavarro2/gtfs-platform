package mx.gtfsplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GtfsPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(GtfsPlatformApplication.class, args);
    }
}
