package mx.gtfsplatform.geo;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

/**
 * Utilidades geodésicas. Para distancias punto-a-punto usamos Haversine (WGS84,
 * radio medio terrestre). Para distancia punto-a-linestring (proximidad parada-shape,
 * sección 10 del prompt) proyectamos a un plano tangente local en metros (equirectangular,
 * válido a escala de una red de autobuses urbana) y medimos en ese plano — evita depender
 * de una consulta PostGIS nativa para algo que se recalcula constantemente en memoria.
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private GeoUtils() {
    }

    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }

    /** Distancia acumulada (metros) en cada vértice de una polilínea lat/lon, empezando en 0. */
    public static double[] cumulativeDistancesMeters(double[] lats, double[] lons) {
        double[] cumulative = new double[lats.length];
        for (int i = 1; i < lats.length; i++) {
            cumulative[i] = cumulative[i - 1] + haversineMeters(lats[i - 1], lons[i - 1], lats[i], lons[i]);
        }
        return cumulative;
    }

    /** Distancia mínima (metros) de un punto a una polilínea, vía proyección plana local en metros. */
    public static double distanceMetersPointToLine(Point point, LineString line) {
        double refLat = point.getY();
        double cosLat = Math.cos(Math.toRadians(refLat));

        Coordinate p = toLocalMeters(point.getY(), point.getX(), refLat, cosLat);
        Coordinate[] lineCoords = line.getCoordinates();

        double best = Double.MAX_VALUE;
        for (int i = 0; i < lineCoords.length - 1; i++) {
            Coordinate a = toLocalMeters(lineCoords[i].y, lineCoords[i].x, refLat, cosLat);
            Coordinate b = toLocalMeters(lineCoords[i + 1].y, lineCoords[i + 1].x, refLat, cosLat);
            double d = distancePointToSegment(p, a, b);
            if (d < best) {
                best = d;
            }
        }
        return best == Double.MAX_VALUE ? 0.0 : best;
    }

    private static Coordinate toLocalMeters(double lat, double lon, double refLat, double cosRefLat) {
        double x = Math.toRadians(lon) * cosRefLat * EARTH_RADIUS_M;
        double y = Math.toRadians(lat) * EARTH_RADIUS_M;
        return new Coordinate(x, y);
    }

    private static double distancePointToSegment(Coordinate p, Coordinate a, Coordinate b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double lengthSq = dx * dx + dy * dy;
        if (lengthSq == 0) {
            return distance(p, a);
        }
        double t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSq;
        t = Math.max(0, Math.min(1, t));
        Coordinate projection = new Coordinate(a.x + t * dx, a.y + t * dy);
        return distance(p, projection);
    }

    private static double distance(Coordinate a, Coordinate b) {
        return Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2));
    }

    public record Projection(int segmentIndex, double t, double distanceMeters) {
    }

    /**
     * Proyecta un punto sobre una polilínea (lats/lons + distancias acumuladas ya
     * calculadas con {@link #cumulativeDistancesMeters}) y devuelve el segmento más
     * cercano, la fracción dentro de ese segmento y la distancia perpendicular. Se usa
     * para: a) distancia parada-shape (aviso de la sección 10), b) distance_along_shape
     * cuando no se definió a mano (métodos B/C de tiempos, sección 16).
     */
    public static Projection projectPointOntoPolyline(double lat, double lon, double[] lats, double[] lons) {
        double refLat = lat;
        double cosLat = Math.cos(Math.toRadians(refLat));
        Coordinate p = toLocalMeters(lat, lon, refLat, cosLat);

        int bestSegment = 0;
        double bestT = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < lats.length - 1; i++) {
            Coordinate a = toLocalMeters(lats[i], lons[i], refLat, cosLat);
            Coordinate b = toLocalMeters(lats[i + 1], lons[i + 1], refLat, cosLat);
            double dx = b.x - a.x;
            double dy = b.y - a.y;
            double lengthSq = dx * dx + dy * dy;
            double t = lengthSq == 0 ? 0 : ((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSq;
            t = Math.max(0, Math.min(1, t));
            Coordinate proj = new Coordinate(a.x + t * dx, a.y + t * dy);
            double d = distance(p, proj);
            if (d < bestDist) {
                bestDist = d;
                bestSegment = i;
                bestT = t;
            }
        }
        return new Projection(bestSegment, bestT, lats.length < 2 ? 0 : bestDist);
    }

    public static double distanceAlongPolylineMeters(Projection projection, double[] cumulative) {
        double segStart = cumulative[projection.segmentIndex()];
        double segEnd = cumulative[projection.segmentIndex() + 1];
        return segStart + projection.t() * (segEnd - segStart);
    }
}
