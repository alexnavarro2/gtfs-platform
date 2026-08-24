package mx.gtfsplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gtfsplatform")
public class GtfsPlatformProperties {

    private final Map map = new Map();
    private final Routing routing = new Routing();
    private final Validator validator = new Validator();
    private final Export export = new Export();
    private final Geocoding geocoding = new Geocoding();

    public Map getMap() {
        return map;
    }

    public Routing getRouting() {
        return routing;
    }

    public Validator getValidator() {
        return validator;
    }

    public Export getExport() {
        return export;
    }

    public Geocoding getGeocoding() {
        return geocoding;
    }

    public static class Map {
        private String tileUrl;

        public String getTileUrl() {
            return tileUrl;
        }

        public void setTileUrl(String tileUrl) {
            this.tileUrl = tileUrl;
        }
    }

    public static class Routing {
        // Default "osrm" contra el demo público de OSRM tras probar 15 pares de
        // puntos reales en Hermosillo: 15/15 éxito, ~0.9s promedio. Igual que
        // geocoding, "manual" (sin red vial) sigue disponible por config — ver
        // docs/ARCHITECTURE-PLAN.md sección I.
        private String provider = "osrm";
        private String osrmUrl = "https://router.project-osrm.org";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getOsrmUrl() {
            return osrmUrl;
        }

        public void setOsrmUrl(String osrmUrl) {
            this.osrmUrl = osrmUrl;
        }
    }

    public static class Validator {
        private String jarPath;
        private int timeoutSeconds = 180;

        public String getJarPath() {
            return jarPath;
        }

        public void setJarPath(String jarPath) {
            this.jarPath = jarPath;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static class Export {
        private String outputDir;

        public String getOutputDir() {
            return outputDir;
        }

        public void setOutputDir(String outputDir) {
            this.outputDir = outputDir;
        }
    }

    public static class Geocoding {
        // Default "esri" tras probar 100 puntos reales: Overpass dio 0% de éxito
        // (timeouts de ~19s) mientras Esri dio 76% con ~0.5s de latencia. Ver
        // docs/ARCHITECTURE-PLAN.md sección I para el detalle de la comparación.
        private String provider = "esri";
        private String overpassUrl = "https://overpass-api.de/api/interpreter";
        private double searchRadiusMeters = 35;
        private int timeoutSeconds = 4;
        private String esriApiKey;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getOverpassUrl() {
            return overpassUrl;
        }

        public void setOverpassUrl(String overpassUrl) {
            this.overpassUrl = overpassUrl;
        }

        public double getSearchRadiusMeters() {
            return searchRadiusMeters;
        }

        public void setSearchRadiusMeters(double searchRadiusMeters) {
            this.searchRadiusMeters = searchRadiusMeters;
        }

        public String getEsriApiKey() {
            return esriApiKey;
        }

        public void setEsriApiKey(String esriApiKey) {
            this.esriApiKey = esriApiKey;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
