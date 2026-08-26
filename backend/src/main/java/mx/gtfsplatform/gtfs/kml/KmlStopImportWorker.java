package mx.gtfsplatform.gtfs.kml;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.gtfsplatform.domain.FeedVersion;
import mx.gtfsplatform.domain.KmlStopImportJob;
import mx.gtfsplatform.domain.Stop;
import mx.gtfsplatform.geocoding.GeocodingProvider;
import mx.gtfsplatform.gtfs.GtfsIdGenerator;
import mx.gtfsplatform.repository.KmlStopImportJobRepository;
import mx.gtfsplatform.repository.StopRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Ejecuta el import de paradas KML en segundo plano (sección "importar paradas desde
 * KML"). Separado de KmlImportService a propósito: @Async solo funciona a través del
 * proxy de Spring, así que el método asíncrono necesita estar en OTRO bean — llamarlo
 * desde el mismo objeto (self-invocation) lo correría de forma síncrona sin avisar.
 *
 * A propósito NO es @Transactional de punta a punta: cada parada se guarda con su
 * propio commit (saveAndFlush), igual que el progreso del job — así una consulta de
 * polling en otra petición ve el avance real mientras corre, y si algo falla a medio
 * camino (ej. se cae la conexión a internet del geocoder), las paradas ya creadas no
 * se pierden.
 */
@Service
public class KmlStopImportWorker {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final StopRepository stopRepository;
    private final GeocodingProvider geocodingProvider;
    private final GtfsIdGenerator idGenerator;
    private final KmlStopImportJobRepository jobRepository;

    public KmlStopImportWorker(StopRepository stopRepository, GeocodingProvider geocodingProvider,
            GtfsIdGenerator idGenerator, KmlStopImportJobRepository jobRepository) {
        this.stopRepository = stopRepository;
        this.geocodingProvider = geocodingProvider;
        this.idGenerator = idGenerator;
        this.jobRepository = jobRepository;
    }

    @Async
    public void run(UUID jobId, FeedVersion feedVersion, List<KmlParser.KmlPoint> points) {
        KmlStopImportJob job = jobRepository.findById(jobId).orElseThrow();
        UUID feedVersionId = feedVersion.getId();
        try {
            int processed = 0;
            int geocoded = 0;
            double minLat = Double.NaN;
            double maxLat = Double.NaN;
            double minLon = Double.NaN;
            double maxLon = Double.NaN;
            for (KmlParser.KmlPoint p : points) {
                Optional<String> suggestion;
                try {
                    suggestion = geocodingProvider.suggestStopName(p.lat(), p.lon());
                } catch (RuntimeException e) {
                    // Best-effort (sección 6): una consulta puntual que falla no debe
                    // tirar todo el lote — la parada se crea igual con un nombre de respaldo.
                    suggestion = Optional.empty();
                }
                String name;
                if (suggestion.isPresent()) {
                    name = suggestion.get();
                    geocoded++;
                } else if (p.name() != null && !p.name().isBlank()) {
                    name = p.name();
                } else {
                    name = "Parada importada";
                }

                Stop stop = new Stop();
                stop.setFeedVersion(feedVersion);
                stop.setGtfsId(idGenerator.next("stop", "STOP", feedVersionId, "feed_version_id"));
                stop.setStopName(name);
                stop.setGeom(GEOMETRY_FACTORY.createPoint(new Coordinate(p.lon(), p.lat())));
                stop.setLocationType((short) 0);
                stop.setWheelchairBoarding((short) 0);
                stop.setRowVersion(0L);
                OffsetDateTime now = OffsetDateTime.now();
                stop.setCreatedAt(now);
                stop.setUpdatedAt(now);
                // saveAndFlush, no save: GtfsIdGenerator.next() cuenta filas con JDBC
                // crudo fuera de la sesión de Hibernate — sin flush no ve el INSERT
                // anterior (Hibernate lo difiere) y repite el mismo gtfs_id.
                stopRepository.saveAndFlush(stop);

                minLat = Double.isNaN(minLat) ? p.lat() : Math.min(minLat, p.lat());
                maxLat = Double.isNaN(maxLat) ? p.lat() : Math.max(maxLat, p.lat());
                minLon = Double.isNaN(minLon) ? p.lon() : Math.min(minLon, p.lon());
                maxLon = Double.isNaN(maxLon) ? p.lon() : Math.max(maxLon, p.lon());

                processed++;
                job.setProcessedCount(processed);
                job.setGeocodedCount(geocoded);
                job.setMinLat(minLat);
                job.setMaxLat(maxLat);
                job.setMinLon(minLon);
                job.setMaxLon(maxLon);
                jobRepository.save(job);
            }
            job.setStatus(KmlStopImportJob.Status.DONE.name());
            job.setFinishedAt(OffsetDateTime.now());
            jobRepository.save(job);
        } catch (Exception e) {
            job.setStatus(KmlStopImportJob.Status.FAILED.name());
            job.setErrorMessage(e.getMessage());
            job.setFinishedAt(OffsetDateTime.now());
            jobRepository.save(job);
        }
    }
}
