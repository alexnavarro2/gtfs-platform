package mx.gtfsplatform.geo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoUtilsTest {

    @Test
    void haversineIsZeroForSamePoint() {
        assertEquals(0.0, GeoUtils.haversineMeters(29.08, -110.96, 29.08, -110.96), 0.001);
    }

    @Test
    void haversineMatchesKnownDistanceApproximately() {
        // Un grado de latitud ~111.2 km.
        double d = GeoUtils.haversineMeters(29.0, -110.0, 30.0, -110.0);
        assertTrue(Math.abs(d - 111_195) < 500, "distancia inesperada: " + d);
    }

    @Test
    void cumulativeDistancesAreMonotonicNonDecreasing() {
        double[] lats = {29.0892, 29.0820, 29.0729};
        double[] lons = {-110.9613, -110.9590, -110.9559};
        double[] cumulative = GeoUtils.cumulativeDistancesMeters(lats, lons);
        assertEquals(0.0, cumulative[0]);
        for (int i = 1; i < cumulative.length; i++) {
            assertTrue(cumulative[i] >= cumulative[i - 1], "shape_dist_traveled no debe decrecer (sección 41 del prompt)");
        }
    }

    @Test
    void projectsPointOntoNearestSegmentOfPolyline() {
        double[] lats = {29.0, 29.0};
        double[] lons = {-110.0, -109.99};
        // Punto muy cerca del segmento (pequeño offset en latitud).
        GeoUtils.Projection proj = GeoUtils.projectPointOntoPolyline(29.0001, -109.995, lats, lons);
        assertEquals(0, proj.segmentIndex());
        assertTrue(proj.distanceMeters() < 50);
    }
}
