package mx.gtfsplatform.web;

import mx.gtfsplatform.routing.RoutingProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Sección 9/54 del prompt: construir un recorrido uniendo paradas existentes,
 * ruteado por la red vial (Modo 1/3 — igual que Conveyal construye patterns con su
 * propio motor R5). La geometría devuelta NUNCA se guarda automáticamente — el
 * frontend siempre la trata como propuesta editable hasta que el usuario confirma
 * "Guardar recorrido" (sección 55).
 */
@RestController
@RequestMapping("/api/v1/routing")
public class RoutingController {

    private final RoutingProvider routingProvider;

    public RoutingController(RoutingProvider routingProvider) {
        this.routingProvider = routingProvider;
    }

    @PostMapping("/route")
    public RouteResponse route(@RequestBody RouteRequest request) {
        List<RoutingProvider.Waypoint> waypoints = request.points().stream()
                .map(p -> new RoutingProvider.Waypoint(p.lat(), p.lon()))
                .toList();
        RoutingProvider.RouteGeometry geometry = routingProvider.route(waypoints, RoutingProvider.RoutingProfile.BUS);
        return new RouteResponse(geometry.pointsLatLon(), geometry.routed(), geometry.providerName());
    }

    public record LatLon(double lat, double lon) {
    }

    public record RouteRequest(List<LatLon> points) {
    }

    public record RouteResponse(List<double[]> points, boolean routed, String provider) {
    }
}
