package mx.gtfsplatform.gtfs.export;

import mx.gtfsplatform.domain.*;
import mx.gtfsplatform.gtfs.GtfsTime;
import mx.gtfsplatform.repository.*;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PostgreSQL/PostGIS -> modelo de dominio -> CSV -> ZIP (sección 56 del prompt: el
 * frontend nunca escribe GTFS, todo pasa por aquí). Solo incluye archivos que tienen
 * datos, salvo los obligatorios del núcleo (sección 27: "incluir solamente archivos
 * válidos/necesarios").
 */
@Service
public class GtfsExportServiceImpl {

    private static final DateTimeFormatter GTFS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final FeedVersionRepository feedVersionRepository;
    private final AgencyRepository agencyRepository;
    private final StopRepository stopRepository;
    private final RouteRepository routeRepository;
    private final RoutePatternRepository routePatternRepository;
    private final ShapePointRepository shapePointRepository;
    private final PatternStopRepository patternStopRepository;
    private final ServiceCalendarRepository serviceCalendarRepository;
    private final ServiceExceptionRepository serviceExceptionRepository;
    private final TripRepository tripRepository;
    private final StopTimeRepository stopTimeRepository;
    private final FrequencyEntryRepository frequencyEntryRepository;
    private final RiderCategoryRepository riderCategoryRepository;
    private final FareMediaRepository fareMediaRepository;
    private final FareProductRepository fareProductRepository;
    private final FareLegRuleRepository fareLegRuleRepository;
    private final FareTransferRuleRepository fareTransferRuleRepository;
    private final TransferRuleRepository transferRuleRepository;
    private final ExportArtifactRepository exportArtifactRepository;
    private final String exportOutputDir;

    public GtfsExportServiceImpl(FeedVersionRepository feedVersionRepository,
                                  AgencyRepository agencyRepository,
                                  StopRepository stopRepository,
                                  RouteRepository routeRepository,
                                  RoutePatternRepository routePatternRepository,
                                  ShapePointRepository shapePointRepository,
                                  PatternStopRepository patternStopRepository,
                                  ServiceCalendarRepository serviceCalendarRepository,
                                  ServiceExceptionRepository serviceExceptionRepository,
                                  TripRepository tripRepository,
                                  StopTimeRepository stopTimeRepository,
                                  FrequencyEntryRepository frequencyEntryRepository,
                                  RiderCategoryRepository riderCategoryRepository,
                                  FareMediaRepository fareMediaRepository,
                                  FareProductRepository fareProductRepository,
                                  FareLegRuleRepository fareLegRuleRepository,
                                  FareTransferRuleRepository fareTransferRuleRepository,
                                  TransferRuleRepository transferRuleRepository,
                                  ExportArtifactRepository exportArtifactRepository,
                                  @org.springframework.beans.factory.annotation.Value("${gtfsplatform.export.output-dir}") String exportOutputDir) {
        this.feedVersionRepository = feedVersionRepository;
        this.agencyRepository = agencyRepository;
        this.stopRepository = stopRepository;
        this.routeRepository = routeRepository;
        this.routePatternRepository = routePatternRepository;
        this.shapePointRepository = shapePointRepository;
        this.patternStopRepository = patternStopRepository;
        this.serviceCalendarRepository = serviceCalendarRepository;
        this.serviceExceptionRepository = serviceExceptionRepository;
        this.tripRepository = tripRepository;
        this.stopTimeRepository = stopTimeRepository;
        this.frequencyEntryRepository = frequencyEntryRepository;
        this.riderCategoryRepository = riderCategoryRepository;
        this.fareMediaRepository = fareMediaRepository;
        this.fareProductRepository = fareProductRepository;
        this.fareLegRuleRepository = fareLegRuleRepository;
        this.fareTransferRuleRepository = fareTransferRuleRepository;
        this.transferRuleRepository = transferRuleRepository;
        this.exportArtifactRepository = exportArtifactRepository;
        this.exportOutputDir = exportOutputDir;
    }

    @Transactional(readOnly = true)
    public mx.gtfsplatform.gtfs.GtfsEngine.ExportResult export(UUID feedVersionId) {
        FeedVersion feedVersion = feedVersionRepository.findById(feedVersionId)
                .orElseThrow(() -> new NoSuchElementException("feed_version no encontrado: " + feedVersionId));

        try {
            Path workDir = Files.createTempDirectory("gtfs-export-");

            List<Agency> agencies = agencyRepository.findByFeedVersionId(feedVersionId);
            List<Stop> stops = stopRepository.findByFeedVersionId(feedVersionId);
            List<Route> routes = routeRepository.findByFeedVersionId(feedVersionId);
            List<RoutePattern> patterns = routes.stream()
                    .flatMap(r -> routePatternRepository.findByRouteId(r.getId()).stream())
                    .collect(Collectors.toList());
            List<ServiceCalendar> calendars = serviceCalendarRepository.findByFeedVersionId(feedVersionId);
            List<Trip> trips = patterns.stream()
                    .flatMap(p -> tripRepository.findByRoutePatternId(p.getId()).stream())
                    .collect(Collectors.toList());

            writeAgency(workDir, agencies);
            writeStops(workDir, stops);
            writeRoutes(workDir, routes);
            writeTrips(workDir, trips);
            writeStopTimes(workDir, trips);
            writeCalendar(workDir, calendars);
            writeCalendarDates(workDir, calendars);
            writeShapes(workDir, patterns);
            writeFrequencies(workDir, trips);
            writeFeedInfo(workDir, feedVersion);
            writeFaresIfPresent(workDir, feedVersionId);
            writeTransfersIfPresent(workDir, feedVersionId);

            Path zipPath = zipDirectory(workDir, feedVersion);
            String sha256 = sha256(zipPath);
            long size = Files.size(zipPath);
            Instant now = Instant.now();

            ExportArtifact artifact = ExportArtifact.builder()
                    .feedVersion(feedVersion)
                    .filePath(zipPath.toString())
                    .sha256(sha256)
                    .sizeBytes(size)
                    .generatedAt(now)
                    .build();
            exportArtifactRepository.save(artifact);

            return new mx.gtfsplatform.gtfs.GtfsEngine.ExportResult(zipPath, sha256, size, now);
        } catch (IOException e) {
            throw new RuntimeException("Fallo generando gtfs.zip para feed_version " + feedVersionId, e);
        }
    }

    private void writeAgency(Path dir, List<Agency> agencies) {
        CsvTable.write(dir, "agency.txt", List.of("agency_id", "agency_name", "agency_url",
                "agency_timezone", "agency_lang", "agency_phone", "agency_fare_url", "agency_email"),
                printer -> {
                    for (Agency a : agencies) {
                        printer.printRecord(a.getGtfsId(), a.getAgencyName(), a.getAgencyUrl(),
                                a.getAgencyTimezone(), nullToEmpty(a.getAgencyLang()), nullToEmpty(a.getAgencyPhone()),
                                nullToEmpty(a.getAgencyFareUrl()), nullToEmpty(a.getAgencyEmail()));
                    }
                });
    }

    private void writeStops(Path dir, List<Stop> stops) {
        CsvTable.write(dir, "stops.txt", List.of("stop_id", "stop_code", "stop_name", "tts_stop_name",
                "stop_desc", "stop_lat", "stop_lon", "zone_id", "stop_url", "location_type", "parent_station",
                "stop_timezone", "wheelchair_boarding", "platform_code"),
                printer -> {
                    for (Stop s : stops) {
                        printer.printRecord(s.getGtfsId(), nullToEmpty(s.getStopCode()), nullToEmpty(s.getStopName()),
                                nullToEmpty(s.getTtsStopName()), nullToEmpty(s.getStopDesc()),
                                s.getStopLat(), s.getStopLon(), nullToEmpty(s.getZoneId()), nullToEmpty(s.getStopUrl()),
                                s.getLocationType(), s.getParentStation() == null ? "" : s.getParentStation().getGtfsId(),
                                nullToEmpty(s.getStopTimezone()), s.getWheelchairBoarding(), nullToEmpty(s.getPlatformCode()));
                    }
                });
    }

    private void writeRoutes(Path dir, List<Route> routes) {
        CsvTable.write(dir, "routes.txt", List.of("route_id", "agency_id", "route_short_name",
                "route_long_name", "route_desc", "route_type", "route_url", "route_color", "route_text_color",
                "route_sort_order", "continuous_pickup", "continuous_drop_off", "network_id"),
                printer -> {
                    for (Route r : routes) {
                        printer.printRecord(r.getGtfsId(), r.getAgency().getGtfsId(), nullToEmpty(r.getRouteShortName()),
                                nullToEmpty(r.getRouteLongName()), nullToEmpty(r.getRouteDesc()), r.getRouteType(),
                                nullToEmpty(r.getRouteUrl()), nullToEmpty(r.getRouteColor()), nullToEmpty(r.getRouteTextColor()),
                                r.getRouteSortOrder() == null ? "" : r.getRouteSortOrder(),
                                r.getContinuousPickup() == null ? "" : r.getContinuousPickup(),
                                r.getContinuousDropOff() == null ? "" : r.getContinuousDropOff(),
                                nullToEmpty(r.getNetworkId()));
                    }
                });
    }

    private void writeTrips(Path dir, List<Trip> trips) {
        CsvTable.write(dir, "trips.txt", List.of("route_id", "service_id", "trip_id", "trip_headsign",
                "trip_short_name", "direction_id", "block_id", "shape_id", "wheelchair_accessible", "bikes_allowed"),
                printer -> {
                    for (Trip t : trips) {
                        RoutePattern p = t.getRoutePattern();
                        String headsign = t.getTripHeadsign() != null ? t.getTripHeadsign() : p.getTripHeadsign();
                        printer.printRecord(p.getRoute().getGtfsId(), t.getServiceCalendar().getGtfsId(), t.getGtfsId(),
                                nullToEmpty(headsign), nullToEmpty(t.getTripShortName()), p.getDirectionId(),
                                nullToEmpty(t.getBlockId()), p.getShapeGtfsId(), t.getWheelchairAccessible(), t.getBikesAllowed());
                    }
                });
    }

    private void writeStopTimes(Path dir, List<Trip> trips) {
        CsvTable.write(dir, "stop_times.txt", List.of("trip_id", "arrival_time", "departure_time", "stop_id",
                "stop_sequence", "stop_headsign", "pickup_type", "drop_off_type", "shape_dist_traveled", "timepoint"),
                printer -> {
                    for (Trip t : trips) {
                        List<StopTime> times = stopTimeRepository.findByTripIdOrderByStopSequenceAsc(t.getId());
                        for (StopTime st : times) {
                            printer.printRecord(t.getGtfsId(), GtfsTime.formatFromSeconds(st.getArrivalTimeSec()),
                                    GtfsTime.formatFromSeconds(st.getDepartureTimeSec()),
                                    st.getPatternStop().getStop().getGtfsId(), st.getStopSequence(),
                                    nullToEmpty(st.getStopHeadsign()), st.getPickupType(), st.getDropOffType(),
                                    st.getShapeDistTraveled() == null ? "" : st.getShapeDistTraveled(), st.getTimepoint());
                        }
                    }
                });
    }

    private void writeCalendar(Path dir, List<ServiceCalendar> calendars) {
        if (calendars.isEmpty()) {
            return;
        }
        CsvTable.write(dir, "calendar.txt", List.of("service_id", "monday", "tuesday", "wednesday", "thursday",
                "friday", "saturday", "sunday", "start_date", "end_date"),
                printer -> {
                    for (ServiceCalendar c : calendars) {
                        printer.printRecord(c.getGtfsId(), bit(c.getMonday()), bit(c.getTuesday()), bit(c.getWednesday()),
                                bit(c.getThursday()), bit(c.getFriday()), bit(c.getSaturday()), bit(c.getSunday()),
                                c.getStartDate().format(GTFS_DATE), c.getEndDate().format(GTFS_DATE));
                    }
                });
    }

    private void writeCalendarDates(Path dir, List<ServiceCalendar> calendars) {
        List<ServiceException> all = calendars.stream()
                .flatMap(c -> serviceExceptionRepository.findByServiceCalendarId(c.getId()).stream())
                .collect(Collectors.toList());
        if (all.isEmpty()) {
            return;
        }
        Map<UUID, String> gtfsIdByCalendarId = calendars.stream()
                .collect(Collectors.toMap(ServiceCalendar::getId, ServiceCalendar::getGtfsId));
        CsvTable.write(dir, "calendar_dates.txt", List.of("service_id", "date", "exception_type"),
                printer -> {
                    for (ServiceException e : all) {
                        printer.printRecord(gtfsIdByCalendarId.get(e.getServiceCalendar().getId()),
                                e.getExceptionDate().format(GTFS_DATE), e.getExceptionType());
                    }
                });
    }

    private void writeShapes(Path dir, List<RoutePattern> patterns) {
        List<RoutePattern> withShapes = patterns.stream()
                .filter(p -> !shapePointRepository.findByRoutePatternIdOrderByShapePtSequenceAsc(p.getId()).isEmpty())
                .collect(Collectors.toList());
        if (withShapes.isEmpty()) {
            return;
        }
        CsvTable.write(dir, "shapes.txt", List.of("shape_id", "shape_pt_lat", "shape_pt_lon",
                "shape_pt_sequence", "shape_dist_traveled"),
                printer -> {
                    for (RoutePattern p : withShapes) {
                        List<ShapePoint> points = shapePointRepository.findByRoutePatternIdOrderByShapePtSequenceAsc(p.getId());
                        double[] lats = points.stream().mapToDouble(ShapePoint::getShapePtLat).toArray();
                        double[] lons = points.stream().mapToDouble(ShapePoint::getShapePtLon).toArray();
                        double[] cumulative = mx.gtfsplatform.geo.GeoUtils.cumulativeDistancesMeters(lats, lons);
                        for (int i = 0; i < points.size(); i++) {
                            ShapePoint sp = points.get(i);
                            printer.printRecord(p.getShapeGtfsId(), sp.getShapePtLat(), sp.getShapePtLon(),
                                    sp.getShapePtSequence(), cumulative[i]);
                        }
                    }
                });
    }

    private void writeFrequencies(Path dir, List<Trip> trips) {
        List<Trip> freqTrips = trips.stream().filter(Trip::getFrequencyBased).collect(Collectors.toList());
        if (freqTrips.isEmpty()) {
            return;
        }
        CsvTable.write(dir, "frequencies.txt", List.of("trip_id", "start_time", "end_time", "headway_secs", "exact_times"),
                printer -> {
                    for (Trip t : freqTrips) {
                        for (FrequencyEntry f : frequencyEntryRepository.findByTripId(t.getId())) {
                            printer.printRecord(t.getGtfsId(), GtfsTime.formatFromSeconds(f.getStartTimeSec()),
                                    GtfsTime.formatFromSeconds(f.getEndTimeSec()), f.getHeadwaySecs(), f.getExactTimes());
                        }
                    }
                });
    }

    private void writeFeedInfo(Path dir, FeedVersion fv) {
        if (fv.getFeedPublisherName() == null) {
            return;
        }
        CsvTable.write(dir, "feed_info.txt", List.of("feed_publisher_name", "feed_publisher_url", "feed_lang",
                "default_lang", "feed_start_date", "feed_end_date", "feed_version", "feed_contact_email", "feed_contact_url"),
                printer -> printer.printRecord(fv.getFeedPublisherName(), nullToEmpty(fv.getFeedPublisherUrl()),
                        nullToEmpty(fv.getFeedLang()), nullToEmpty(fv.getDefaultLang()),
                        fv.getFeedStartDate() == null ? "" : fv.getFeedStartDate().format(GTFS_DATE),
                        fv.getFeedEndDate() == null ? "" : fv.getFeedEndDate().format(GTFS_DATE),
                        nullToEmpty(fv.getFeedVersionString()), nullToEmpty(fv.getFeedContactEmail()),
                        nullToEmpty(fv.getFeedContactUrl())));
    }

    private void writeFaresIfPresent(Path dir, UUID feedVersionId) {
        List<RiderCategory> categories = riderCategoryRepository.findByFeedVersionId(feedVersionId);
        if (!categories.isEmpty()) {
            CsvTable.write(dir, "rider_categories.txt", List.of("rider_category_id", "rider_category_name",
                    "is_default_fare_category"),
                    printer -> {
                        for (RiderCategory c : categories) {
                            printer.printRecord(c.getGtfsId(), c.getRiderCategoryName(), c.getIsDefaultFareCategory());
                        }
                    });
        }

        List<FareMedia> media = fareMediaRepository.findByFeedVersionId(feedVersionId);
        if (!media.isEmpty()) {
            CsvTable.write(dir, "fare_media.txt", List.of("fare_media_id", "fare_media_name", "fare_media_type"),
                    printer -> {
                        for (FareMedia m : media) {
                            printer.printRecord(m.getGtfsId(), nullToEmpty(m.getFareMediaName()), m.getFareMediaType());
                        }
                    });
        }

        List<FareProduct> products = fareProductRepository.findByFeedVersionId(feedVersionId);
        if (!products.isEmpty()) {
            CsvTable.write(dir, "fare_products.txt", List.of("fare_product_id", "fare_product_name",
                    "rider_category_id", "fare_media_id", "amount", "currency"),
                    printer -> {
                        for (FareProduct p : products) {
                            printer.printRecord(p.getGtfsId(), p.getFareProductName(),
                                    p.getRiderCategory() == null ? "" : p.getRiderCategory().getGtfsId(),
                                    p.getFareMedia() == null ? "" : p.getFareMedia().getGtfsId(),
                                    p.getAmount(), p.getCurrency());
                        }
                    });
        }

        List<FareLegRule> legRules = fareLegRuleRepository.findByFeedVersionId(feedVersionId);
        if (!legRules.isEmpty()) {
            CsvTable.write(dir, "fare_leg_rules.txt", List.of("leg_group_id", "network_id", "fare_product_id"),
                    printer -> {
                        for (FareLegRule r : legRules) {
                            printer.printRecord(nullToEmpty(r.getGtfsLegGroupId()), nullToEmpty(r.getNetworkId()),
                                    r.getFareProduct().getGtfsId());
                        }
                    });
        }

        List<FareTransferRule> transferRules = fareTransferRuleRepository.findByFeedVersionId(feedVersionId);
        if (!transferRules.isEmpty()) {
            CsvTable.write(dir, "fare_transfer_rules.txt", List.of("from_leg_group_id", "to_leg_group_id",
                    "transfer_count", "duration_limit", "duration_limit_type", "fare_transfer_type", "fare_product_id"),
                    printer -> {
                        for (FareTransferRule r : transferRules) {
                            printer.printRecord(nullToEmpty(r.getFromLegGroupId()), nullToEmpty(r.getToLegGroupId()),
                                    r.getTransferCount() == null ? "" : r.getTransferCount(),
                                    r.getDurationLimitSecs() == null ? "" : r.getDurationLimitSecs(),
                                    r.getDurationLimitType() == null ? "" : r.getDurationLimitType(),
                                    r.getFareTransferType(),
                                    r.getFareProduct() == null ? "" : r.getFareProduct().getGtfsId());
                        }
                    });
        }
    }

    private void writeTransfersIfPresent(Path dir, UUID feedVersionId) {
        List<TransferRule> transfers = transferRuleRepository.findByFeedVersionId(feedVersionId);
        if (transfers.isEmpty()) {
            return;
        }
        CsvTable.write(dir, "transfers.txt", List.of("from_stop_id", "to_stop_id", "transfer_type", "min_transfer_time"),
                printer -> {
                    for (TransferRule t : transfers) {
                        printer.printRecord(t.getFromStop().getGtfsId(), t.getToStop().getGtfsId(),
                                t.getTransferType(), t.getMinTransferTimeSec() == null ? "" : t.getMinTransferTimeSec());
                    }
                });
    }

    private Path zipDirectory(Path dir, FeedVersion feedVersion) throws IOException {
        Path outDir = Path.of(exportOutputDir);
        Files.createDirectories(outDir);
        Path zipPath = outDir.resolve("feed-version-" + feedVersion.getId() + ".zip");
        try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(zipPath))) {
            try (var files = Files.list(dir)) {
                for (Path f : files.sorted().collect(Collectors.toList())) {
                    zos.putNextEntry(new java.util.zip.ZipEntry(f.getFileName().toString()));
                    Files.copy(f, zos);
                    zos.closeEntry();
                }
            }
        }
        return zipPath;
    }

    private String sha256(Path file) throws IOException {
        try (InputStream is = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) {
                digest.update(buf, 0, read);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String bit(Boolean b) {
        return Boolean.TRUE.equals(b) ? "1" : "0";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
