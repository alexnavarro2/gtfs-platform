package mx.gtfsplatform.routing;

import java.util.List;

/**
 * SPI de ruteo (sección 54 del prompt). La geometría que devuelve NUNCA se acepta
 * automáticamente como definitiva (sección 55) — el frontend siempre la trata como
 * punto de partida editable.
 */
public interface RoutingProvider {

    RouteGeometry route(List<Waypoint> waypoints, RoutingProfile profile);

    /**
     * Pega una traza ya existente (ej. una línea importada de KML, con puntos que no
     * caen exactamente sobre la calle) a la red vial real, preservando el recorrido
     * original — a diferencia de route(), que calcula la mejor ruta ENTRE waypoints
     * sueltos. Igual que route(), nunca es definitivo por sí solo: si falla o no hay
     * proveedor configurado, se degrada a devolver la traza tal cual (matched=false).
     */
    RouteGeometry match(List<Waypoint> points);

    record Waypoint(double lat, double lon) {
    }

    record RouteGeometry(List<double[]> pointsLatLon, boolean routed, String providerName) {
    }

    enum RoutingProfile { BUS, WALK }
}
