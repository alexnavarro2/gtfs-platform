package mx.gtfsplatform.geocoding;

import java.util.Optional;

/**
 * SPI de nomenclatura de paradas (sección 4/6 del prompt: abstracción de proveedor,
 * nunca acoplado a un servicio público concreto). A diferencia del autocomplete
 * indiscriminado que sí evitamos, esto es UNA sola consulta puntual disparada por una
 * acción explícita del usuario (crear una parada) — el mismo patrón que usa Conveyal
 * Data Tools para sugerir "Calle A & Avenida B" a partir de la intersección más
 * próxima. El nombre sugerido siempre queda editable, nunca se aplica solo.
 */
public interface GeocodingProvider {

    /** Nombre sugerido para una parada nueva en base a las vías más cercanas al punto, o vacío si no hay match. */
    Optional<String> suggestStopName(double lat, double lon);
}
