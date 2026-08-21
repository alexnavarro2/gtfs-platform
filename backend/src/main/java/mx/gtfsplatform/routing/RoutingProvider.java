package mx.gtfsplatform.routing;

import java.util.List;

/**
 * SPI de ruteo (sección 54 del prompt). La geometría que devuelve NUNCA se acepta
 * automáticamente como definitiva (sección 55) — el frontend siempre la trata como
 * punto de partida editable.
 */
public interface RoutingProvider {

    RouteGeometry route(List<Waypoint> waypoints, RoutingProfile profile);

    record Waypoint(double lat, double lon) {
    }

    record RouteGeometry(List<double[]> pointsLatLon, boolean routed, String providerName) {
    }

    enum RoutingProfile { BUS, WALK }
}
