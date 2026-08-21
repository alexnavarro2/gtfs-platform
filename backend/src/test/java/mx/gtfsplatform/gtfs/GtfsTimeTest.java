package mx.gtfsplatform.gtfs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GtfsTimeTest {

    @Test
    void parsesRegularTime() {
        assertEquals(5 * 3600 + 20 * 60, GtfsTime.parseToSeconds("05:20:00"));
    }

    @Test
    void parsesTimesPastMidnightWithoutRollingOverToNextDay() {
        // Sección 16/62 del prompt: 25:30:00 debe seguir siendo el "mismo" service day,
        // nunca reinterpretarse como 01:30:00 del día siguiente.
        assertEquals(25 * 3600 + 30 * 60, GtfsTime.parseToSeconds("25:30:00"));
    }

    @Test
    void formatsBackToTheSameHhMmSsIncludingPastMidnight() {
        assertEquals("24:15:00", GtfsTime.formatFromSeconds(24 * 3600 + 15 * 60));
        assertEquals("05:00:00", GtfsTime.formatFromSeconds(5 * 3600));
    }

    @Test
    void rejectsNegativeSeconds() {
        assertThrows(IllegalArgumentException.class, () -> GtfsTime.formatFromSeconds(-1));
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(IllegalArgumentException.class, () -> GtfsTime.parseToSeconds("05:20"));
    }
}
