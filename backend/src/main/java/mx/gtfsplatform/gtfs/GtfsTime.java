package mx.gtfsplatform.gtfs;

/**
 * GTFS permite horas >= 24:00:00 para servicio que cruza medianoche (sección 16/62 del
 * prompt: "no fallar con horas >24:00", "no convertir erróneamente al día siguiente").
 * Por eso el tiempo se maneja siempre como segundos-desde-medianoche del service day
 * (entero simple), nunca como java.time.LocalTime — LocalTime no admite horas >= 24.
 */
public final class GtfsTime {

    private GtfsTime() {
    }

    public static int parseToSeconds(String hhmmss) {
        String[] parts = hhmmss.trim().split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Hora GTFS inválida (se esperaba HH:MM:SS): " + hhmmss);
        }
        int h = Integer.parseInt(parts[0]);
        int m = Integer.parseInt(parts[1]);
        int s = Integer.parseInt(parts[2]);
        return h * 3600 + m * 60 + s;
    }

    public static String formatFromSeconds(int totalSeconds) {
        if (totalSeconds < 0) {
            throw new IllegalArgumentException("Segundos GTFS no pueden ser negativos: " + totalSeconds);
        }
        int h = totalSeconds / 3600;
        int m = (totalSeconds % 3600) / 60;
        int s = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }
}
