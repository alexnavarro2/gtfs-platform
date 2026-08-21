package mx.gtfsplatform.routing;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementación por defecto (Fase 1): sin motor de ruteo externo. Conecta los
 * waypoints con segmentos rectos como punto de partida — el usuario dibuja/ajusta
 * manualmente desde ahí (Modo 2 de la sección 9). routed=false le indica al frontend
 * que muestre el aviso de "geometría no ruteada, edítala".
 */
public class ManualRoutingProvider implements RoutingProvider {

    @Override
    public RouteGeometry route(List<Waypoint> waypoints, RoutingProfile profile) {
        List<double[]> points = new ArrayList<>();
        for (Waypoint w : waypoints) {
            points.add(new double[]{w.lat(), w.lon()});
        }
        return new RouteGeometry(points, false, "manual");
    }
}
