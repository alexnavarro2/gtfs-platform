package mx.gtfsplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gtfsplatform")
public class GtfsPlatformProperties {

    private final Map map = new Map();
    private final Routing routing = new Routing();
    private final Validator validator = new Validator();
    private final Export export = new Export();

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
        private String provider = "manual";
        private String osrmUrl;

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
}
