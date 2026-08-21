package mx.gtfsplatform.geocoding;

import java.util.Optional;

/** Sin sugerencia de nombre: el usuario siempre escribe el nombre a mano. */
public class NoOpGeocodingProvider implements GeocodingProvider {

    @Override
    public Optional<String> suggestStopName(double lat, double lon) {
        return Optional.empty();
    }
}
