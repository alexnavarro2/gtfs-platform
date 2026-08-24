package mx.gtfsplatform.gtfs.importer;

import mx.gtfsplatform.domain.*;
import mx.gtfsplatform.gtfs.GtfsEngine;
import mx.gtfsplatform.gtfs.GtfsTime;
import mx.gtfsplatform.repository.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ZIP -> modelo de dominio (sección 28 del prompt). Protegido contra Zip Slip
 * (sección 50): cada entrada se valida contra el directorio destino antes de escribirse.
 * Agrupa trips con la misma ruta + shape_id + secuencia de paradas + direction_id bajo
 * un mismo RoutePattern (sección 28: "agrupar bajo un mismo RoutePattern"), reconstruyendo
 * así lo que produjo nuestro propio exportador sin duplicar patrones por cada trip.
 */
@Service
public class GtfsImportServiceImpl {

    private static final DateTimeFormatter GTFS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final FeedRepository feedRepository;
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
    private final FareProductRepository fareProductRepository;
    private final FareLegRuleRepository fareLegRuleRepository;

    public GtfsImportServiceImpl(FeedRepository feedRepository, FeedVersionRepository feedVersionRepository,
                                  AgencyRepository agencyRepository, StopRepository stopRepository,
                                  RouteRepository routeRepository, RoutePatternRepository routePatternRepository,
                                  ShapePointRepository shapePointRepository, PatternStopRepository patternStopRepository,
                                  ServiceCalendarRepository serviceCalendarRepository,
                                  ServiceExceptionRepository serviceExceptionRepository,
                                  TripRepository tripRepository, StopTimeRepository stopTimeRepository,
                                  FrequencyEntryRepository frequencyEntryRepository,
                                  FareProductRepository fareProductRepository,
                                  FareLegRuleRepository fareLegRuleRepository) {
        this.feedRepository = feedRepository;
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
        this.fareProductRepository = fareProductRepository;
        this.fareLegRuleRepository = fareLegRuleRepository;
    }

    @Transactional
    public GtfsEngine.ImportResult importFeed(UUID feedId, InputStream zipInputStream, String originalFileName) {
        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() -> new NoSuchElementException("feed no encontrado: " + feedId));

        Path extractDir;
        try {
            extractDir = extractZipSafely(zipInputStream);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer " + originalFileName + ": " + e.getMessage(), e);
        }

        int nextVersionNumber = feedVersionRepository.findByFeedId(feedId).stream()
                .map(FeedVersion::getVersionNumber).max(Comparator.naturalOrder()).orElse(0) + 1;
        OffsetDateTime now = OffsetDateTime.now();
        FeedVersion feedVersion = feedVersionRepository.save(FeedVersion.builder()
                .feed(feed)
                .versionNumber(nextVersionNumber)
                .status(FeedVersionStatus.DRAFT)
                .rowVersion(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());

        Map<String, Integer> counts = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        Map<String, Agency> agencyByGtfsId = importAgencies(extractDir, feedVersion, counts);
        Map<String, Stop> stopByGtfsId = importStops(extractDir, feedVersion, counts);
        Map<String, Route> routeByGtfsId = importRoutes(extractDir, feedVersion, agencyByGtfsId, counts, warnings);
        Map<String, ServiceCalendar> calendarByGtfsId = importCalendar(extractDir, feedVersion, counts);
        importCalendarDates(extractDir, calendarByGtfsId, counts, warnings);
        Map<String, List<double[]>> shapePointsByShapeId = readShapes(extractDir);

        importTripsAndStopTimes(extractDir, feedVersion, routeByGtfsId, calendarByGtfsId, stopByGtfsId,
                shapePointsByShapeId, counts, warnings);
        importFrequencies(extractDir, counts, warnings);
        importFeedInfo(extractDir, feedVersion);
        importFaresV1(extractDir, feedVersion, routeByGtfsId, counts, warnings);

        feedVersionRepository.save(feedVersion);

        return new GtfsEngine.ImportResult(feedVersion.getId(), counts, warnings);
    }

    // ---- Zip Slip: cada entrada se resuelve contra el directorio destino y se verifica
    // que siga contenida en él antes de escribir nada (sección 50). ----
    private Path extractZipSafely(InputStream zipInputStream) throws IOException {
        Path targetDir = Files.createTempDirectory("gtfs-import-").normalize();
        try (ZipInputStream zis = new ZipInputStream(zipInputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = Path.of(entry.getName()).getFileName().toString();
                if (name.isBlank() || name.contains("..")) {
                    continue;
                }
                Path resolved = targetDir.resolve(name).normalize();
                if (!resolved.startsWith(targetDir)) {
                    throw new IOException("Entrada de ZIP inválida (path traversal): " + entry.getName());
                }
                Files.copy(zis, resolved);
            }
        }
        return targetDir;
    }

    private CSVParser openCsv(Path dir, String fileName) throws IOException {
        Path file = dir.resolve(fileName);
        if (!Files.exists(file)) {
            return null;
        }
        Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
        return CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader);
    }

    private Map<String, Agency> importAgencies(Path dir, FeedVersion fv, Map<String, Integer> counts) {
        Map<String, Agency> result = new LinkedHashMap<>();
        try (CSVParser parser = openCsv(dir, "agency.txt")) {
            if (parser == null) {
                return result;
            }
            OffsetDateTime now = OffsetDateTime.now();
            int n = 0;
            for (CSVRecord r : parser) {
                Agency a = Agency.builder()
                        .feedVersion(fv)
                        .gtfsId(get(r, "agency_id"))
                        .agencyName(get(r, "agency_name"))
                        .agencyUrl(get(r, "agency_url"))
                        .agencyTimezone(get(r, "agency_timezone"))
                        .agencyLang(get(r, "agency_lang"))
                        .agencyPhone(get(r, "agency_phone"))
                        .agencyFareUrl(get(r, "agency_fare_url"))
                        .agencyEmail(get(r, "agency_email"))
                        .createdAt(now).updatedAt(now)
                        .build();
                a = agencyRepository.save(a);
                result.put(a.getGtfsId(), a);
                n++;
            }
            counts.put("agency", n);
        } catch (IOException e) {
            throw new RuntimeException("Error leyendo agency.txt", e);
        }
        return result;
    }

    private Map<String, Stop> importStops(Path dir, FeedVersion fv, Map<String, Integer> counts) {
        Map<String, Stop> result = new LinkedHashMap<>();
        Map<String, String> parentByGtfsId = new HashMap<>();
        try (CSVParser parser = openCsv(dir, "stops.txt")) {
            if (parser == null) {
                return result;
            }
            OffsetDateTime now = OffsetDateTime.now();
            int n = 0;
            for (CSVRecord r : parser) {
                double lat = Double.parseDouble(get(r, "stop_lat"));
                double lon = Double.parseDouble(get(r, "stop_lon"));
                Stop s = Stop.builder()
                        .feedVersion(fv)
                        .gtfsId(get(r, "stop_id"))
                        .stopCode(get(r, "stop_code"))
                        .stopName(get(r, "stop_name"))
                        .ttsStopName(get(r, "tts_stop_name"))
                        .stopDesc(get(r, "stop_desc"))
                        .geom(GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat)))
                        .zoneId(get(r, "zone_id"))
                        .stopUrl(get(r, "stop_url"))
                        .locationType(shortOrDefault(r, "location_type", (short) 0))
                        .stopTimezone(get(r, "stop_timezone"))
                        .wheelchairBoarding(shortOrDefault(r, "wheelchair_boarding", (short) 0))
                        .platformCode(get(r, "platform_code"))
                        .rowVersion(0L)
                        .createdAt(now).updatedAt(now)
                        .build();
                s = stopRepository.save(s);
                result.put(s.getGtfsId(), s);
                String parent = get(r, "parent_station");
                if (parent != null && !parent.isBlank()) {
                    parentByGtfsId.put(s.getGtfsId(), parent);
                }
                n++;
            }
            counts.put("stops", n);
        } catch (IOException e) {
            throw new RuntimeException("Error leyendo stops.txt", e);
        }
        for (Map.Entry<String, String> e : parentByGtfsId.entrySet()) {
            Stop child = result.get(e.getKey());
            Stop parent = result.get(e.getValue());
            if (child != null && parent != null) {
                child.setParentStation(parent);
                stopRepository.save(child);
            }
        }
        return result;
    }

    private Map<String, Route> importRoutes(Path dir, FeedVersion fv, Map<String, Agency> agencies,
                                             Map<String, Integer> counts, List<String> warnings) {
        Map<String, Route> result = new LinkedHashMap<>();
        try (CSVParser parser = openCsv(dir, "routes.txt")) {
            if (parser == null) {
                return result;
            }
            OffsetDateTime now = OffsetDateTime.now();
            int n = 0;
            for (CSVRecord r : parser) {
                Agency agency = agencies.get(get(r, "agency_id"));
                if (agency == null && !agencies.isEmpty()) {
                    agency = agencies.values().iterator().next();
                    warnings.add("routes.txt: route_id=" + get(r, "route_id")
                            + " sin agency_id válido, se asignó la primera agencia del feed");
                }
                Route route = Route.builder()
                        .feedVersion(fv)
                        .gtfsId(get(r, "route_id"))
                        .agency(agency)
                        .routeShortName(get(r, "route_short_name"))
                        .routeLongName(get(r, "route_long_name"))
                        .routeDesc(get(r, "route_desc"))
                        .routeType(intOrDefault(r, "route_type", 3))
                        .routeUrl(get(r, "route_url"))
                        .routeColor(get(r, "route_color"))
                        .routeTextColor(get(r, "route_text_color"))
                        .routeSortOrder(intOrNull(r, "route_sort_order"))
                        .createdAt(now).updatedAt(now)
                        .build();
                route = routeRepository.save(route);
                result.put(route.getGtfsId(), route);
                n++;
            }
            counts.put("routes", n);
        } catch (IOException e) {
            throw new RuntimeException("Error leyendo routes.txt", e);
        }
        return result;
    }

    private Map<String, ServiceCalendar> importCalendar(Path dir, FeedVersion fv, Map<String, Integer> counts) {
        Map<String, ServiceCalendar> result = new LinkedHashMap<>();
        try (CSVParser parser = openCsv(dir, "calendar.txt")) {
            if (parser == null) {
                return result;
            }
            OffsetDateTime now = OffsetDateTime.now();
            int n = 0;
            for (CSVRecord r : parser) {
                ServiceCalendar c = ServiceCalendar.builder()
                        .feedVersion(fv)
                        .gtfsId(get(r, "service_id"))
                        .name(get(r, "service_id"))
                        .monday(bool(r, "monday")).tuesday(bool(r, "tuesday")).wednesday(bool(r, "wednesday"))
                        .thursday(bool(r, "thursday")).friday(bool(r, "friday")).saturday(bool(r, "saturday"))
                        .sunday(bool(r, "sunday"))
                        .startDate(LocalDate.parse(get(r, "start_date"), GTFS_DATE))
                        .endDate(LocalDate.parse(get(r, "end_date"), GTFS_DATE))
                        .createdAt(now).updatedAt(now)
                        .build();
                c = serviceCalendarRepository.save(c);
                result.put(c.getGtfsId(), c);
                n++;
            }
            counts.put("calendar", n);
        } catch (IOException e) {
            throw new RuntimeException("Error leyendo calendar.txt", e);
        }
        return result;
    }

    private void importCalendarDates(Path dir, Map<String, ServiceCalendar> calendars, Map<String, Integer> counts,
                                      List<String> warnings) {
        try (CSVParser parser = openCsv(dir, "calendar_dates.txt")) {
            if (parser == null) {
                return;
            }
            int n = 0;
            for (CSVRecord r : parser) {
                String serviceId = get(r, "service_id");
                ServiceCalendar calendar = calendars.get(serviceId);
                if (calendar == null) {
                    // service_id que solo existe vía calendar_dates.txt (sin calendar.txt) — sección 13.
                    calendar = ServiceCalendar.builder()
                            .feedVersion(calendars.values().stream().findFirst().map(ServiceCalendar::getFeedVersion).orElse(null))
                            .gtfsId(serviceId).name(serviceId)
                            .monday(false).tuesday(false).wednesday(false).thursday(false)
                            .friday(false).saturday(false).sunday(false)
                            .startDate(LocalDate.parse(get(r, "date"), GTFS_DATE))
                            .endDate(LocalDate.parse(get(r, "date"), GTFS_DATE))
                            .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                            .build();
                    if (calendar.getFeedVersion() == null) {
                        warnings.add("calendar_dates.txt: service_id=" + serviceId + " no se pudo asociar a un feed_version");
                        continue;
                    }
                    calendar = serviceCalendarRepository.save(calendar);
                    calendars.put(serviceId, calendar);
                }
                serviceExceptionRepository.save(ServiceException.builder()
                        .serviceCalendar(calendar)
                        .exceptionDate(LocalDate.parse(get(r, "date"), GTFS_DATE))
                        .exceptionType(Short.parseShort(get(r, "exception_type")))
                        .build());
                n++;
            }
            counts.put("calendar_dates", n);
        } catch (IOException e) {
            throw new RuntimeException("Error leyendo calendar_dates.txt", e);
        }
    }

    private Map<String, List<double[]>> readShapes(Path dir) {
        Map<String, List<double[]>> byShapeId = new LinkedHashMap<>();
        try (CSVParser parser = openCsv(dir, "shapes.txt")) {
            if (parser == null) {
                return byShapeId;
            }
            List<CSVRecord> all = parser.getRecords();
            all.sort(Comparator.comparingInt(r -> Integer.parseInt(get(r, "shape_pt_sequence"))));
            for (CSVRecord r : all) {
                byShapeId.computeIfAbsent(get(r, "shape_id"), k -> new ArrayList<>())
                        .add(new double[]{Double.parseDouble(get(r, "shape_pt_lat")), Double.parseDouble(get(r, "shape_pt_lon"))});
            }
        } catch (IOException e) {
            throw new RuntimeException("Error leyendo shapes.txt", e);
        }
        return byShapeId;
    }

    private void importTripsAndStopTimes(Path dir, FeedVersion fv, Map<String, Route> routes,
                                          Map<String, ServiceCalendar> calendars, Map<String, Stop> stops,
                                          Map<String, List<double[]>> shapesByShapeId, Map<String, Integer> counts,
                                          List<String> warnings) {
        List<CSVRecord> tripRecords;
        try (CSVParser parser = openCsv(dir, "trips.txt")) {
            if (parser == null) {
                return;
            }
            tripRecords = parser.getRecords();
        } catch (IOException e) {
            throw new RuntimeException("Error leyendo trips.txt", e);
        }

        Map<String, List<CSVRecord>> stopTimesByTripId = new HashMap<>();
        try (CSVParser parser = openCsv(dir, "stop_times.txt")) {
            if (parser != null) {
                for (CSVRecord r : parser) {
                    stopTimesByTripId.computeIfAbsent(get(r, "trip_id"), k -> new ArrayList<>()).add(r);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error leyendo stop_times.txt", e);
        }
        stopTimesByTripId.values().forEach(list ->
                list.sort(Comparator.comparingInt(r -> Integer.parseInt(get(r, "stop_sequence")))));

        // Agrupa trips por (route_id, shape_id, direction_id, secuencia de stop_id) -> un RoutePattern.
        record PatternKey(String routeId, String shapeId, String directionId, List<String> stopSequence) {
        }
        Map<PatternKey, RoutePattern> patternByKey = new LinkedHashMap<>();
        Map<PatternKey, List<PatternStop>> patternStopsByKey = new HashMap<>();
        int tripCount = 0, stopTimeCount = 0;

        for (CSVRecord tr : tripRecords) {
            String routeId = get(tr, "route_id");
            Route route = routes.get(routeId);
            if (route == null) {
                warnings.add("trips.txt: trip_id=" + get(tr, "trip_id") + " referencia route_id inexistente, se omite");
                continue;
            }
            String tripId = get(tr, "trip_id");
            List<CSVRecord> times = stopTimesByTripId.getOrDefault(tripId, List.of());
            List<String> stopSeq = times.stream().map(r -> get(r, "stop_id")).toList();
            String shapeId = get(tr, "shape_id");
            String directionId = get(tr, "direction_id");
            PatternKey key = new PatternKey(routeId, shapeId == null ? "" : shapeId,
                    directionId == null ? "0" : directionId, stopSeq);

            RoutePattern pattern = patternByKey.get(key);
            if (pattern == null) {
                pattern = RoutePattern.builder()
                        .route(route)
                        .shapeGtfsId(shapeId != null && !shapeId.isBlank() ? shapeId : ("SHAPE_" + tripId))
                        .name((directionId != null && directionId.equals("1")) ? "Sentido 2" : "Sentido 1")
                        .directionId(directionId != null ? Short.parseShort(directionId) : 0)
                        .tripHeadsign(get(tr, "trip_headsign"))
                        .rowVersion(0L)
                        .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                        .build();
                pattern = routePatternRepository.save(pattern);
                patternByKey.put(key, pattern);

                List<double[]> shapePts = shapesByShapeId.get(shapeId);
                if (shapePts != null) {
                    List<ShapePoint> toSave = new ArrayList<>();
                    for (int i = 0; i < shapePts.size(); i++) {
                        toSave.add(ShapePoint.builder().routePattern(pattern).shapePtSequence(i)
                                .shapePtLat(shapePts.get(i)[0]).shapePtLon(shapePts.get(i)[1]).build());
                    }
                    shapePointRepository.saveAll(toSave);
                }

                List<PatternStop> patStops = new ArrayList<>();
                for (int i = 0; i < times.size(); i++) {
                    CSVRecord st = times.get(i);
                    Stop stop = stops.get(get(st, "stop_id"));
                    if (stop == null) {
                        continue;
                    }
                    patStops.add(PatternStop.builder().routePattern(pattern).stop(stop).stopSequence(i)
                            .defaultTimepoint(shortOrDefault(st, "timepoint", (short) 1))
                            .defaultPickupType(shortOrDefault(st, "pickup_type", (short) 0))
                            .defaultDropOffType(shortOrDefault(st, "drop_off_type", (short) 0))
                            .build());
                }
                patStops = patternStopRepository.saveAll(patStops);
                patternStopsByKey.put(key, patStops);
            }

            ServiceCalendar calendar = calendars.get(get(tr, "service_id"));
            if (calendar == null) {
                warnings.add("trips.txt: trip_id=" + tripId + " referencia service_id inexistente, se omite");
                continue;
            }

            Trip trip = Trip.builder()
                    .routePattern(pattern)
                    .serviceCalendar(calendar)
                    .gtfsId(tripId)
                    .tripHeadsign(get(tr, "trip_headsign"))
                    .tripShortName(get(tr, "trip_short_name"))
                    .blockId(get(tr, "block_id"))
                    .wheelchairAccessible(shortOrDefault(tr, "wheelchair_accessible", (short) 0))
                    .bikesAllowed(shortOrDefault(tr, "bikes_allowed", (short) 0))
                    .frequencyBased(false)
                    .build();
            trip = tripRepository.save(trip);
            tripCount++;

            List<PatternStop> patStops = patternStopsByKey.get(key);
            List<StopTime> stopTimes = new ArrayList<>();
            for (int i = 0; i < times.size() && i < patStops.size(); i++) {
                CSVRecord st = times.get(i);
                stopTimes.add(StopTime.builder()
                        .trip(trip).patternStop(patStops.get(i)).stopSequence(i)
                        .arrivalTimeSec(GtfsTime.parseToSeconds(get(st, "arrival_time")))
                        .departureTimeSec(GtfsTime.parseToSeconds(get(st, "departure_time")))
                        .stopHeadsign(get(st, "stop_headsign"))
                        .pickupType(shortOrDefault(st, "pickup_type", (short) 0))
                        .dropOffType(shortOrDefault(st, "drop_off_type", (short) 0))
                        .shapeDistTraveled(doubleOrNull(st, "shape_dist_traveled"))
                        .timepoint(shortOrDefault(st, "timepoint", (short) 1))
                        .build());
            }
            stopTimeRepository.saveAll(stopTimes);
            stopTimeCount += stopTimes.size();
        }
        counts.put("trips", tripCount);
        counts.put("stop_times", stopTimeCount);
        counts.put("route_patterns", patternByKey.size());
    }

    private void importFrequencies(Path dir, Map<String, Integer> counts, List<String> warnings) {
        try (CSVParser parser = openCsv(dir, "frequencies.txt")) {
            if (parser == null) {
                return;
            }
            // Se arma el índice trip_id -> Trip una sola vez en vez de un findAll() por
            // fila (frequencies.txt puede tener cientos de filas en un feed real).
            Map<String, Trip> tripByGtfsId = new HashMap<>();
            for (Trip t : tripRepository.findAll()) {
                tripByGtfsId.put(t.getGtfsId(), t);
            }
            int n = 0;
            for (CSVRecord r : parser) {
                String tripGtfsId = get(r, "trip_id");
                Trip trip = tripByGtfsId.get(tripGtfsId);
                if (trip == null) {
                    warnings.add("frequencies.txt: trip_id=" + tripGtfsId + " no encontrado, se omite");
                    continue;
                }
                trip.setFrequencyBased(true);
                tripRepository.save(trip);
                frequencyEntryRepository.save(FrequencyEntry.builder()
                        .trip(trip)
                        .startTimeSec(GtfsTime.parseToSeconds(get(r, "start_time")))
                        .endTimeSec(GtfsTime.parseToSeconds(get(r, "end_time")))
                        .headwaySecs(Integer.parseInt(get(r, "headway_secs")))
                        .exactTimes(shortOrDefault(r, "exact_times", (short) 0))
                        .build());
                n++;
            }
            counts.put("frequencies", n);
        } catch (IOException e) {
            throw new RuntimeException("Error leyendo frequencies.txt", e);
        }
    }

    private void importFeedInfo(Path dir, FeedVersion fv) {
        try (CSVParser parser = openCsv(dir, "feed_info.txt")) {
            if (parser == null) {
                return;
            }
            for (CSVRecord r : parser) {
                fv.setFeedPublisherName(get(r, "feed_publisher_name"));
                fv.setFeedPublisherUrl(get(r, "feed_publisher_url"));
                fv.setFeedLang(get(r, "feed_lang"));
                fv.setDefaultLang(get(r, "default_lang"));
                String start = get(r, "feed_start_date");
                String end = get(r, "feed_end_date");
                if (start != null && !start.isBlank()) {
                    fv.setFeedStartDate(LocalDate.parse(start, GTFS_DATE));
                }
                if (end != null && !end.isBlank()) {
                    fv.setFeedEndDate(LocalDate.parse(end, GTFS_DATE));
                }
                fv.setFeedVersionString(get(r, "feed_version"));
                fv.setFeedContactEmail(get(r, "feed_contact_email"));
                fv.setFeedContactUrl(get(r, "feed_contact_url"));
                break;
            }
        } catch (IOException e) {
            throw new RuntimeException("Error leyendo feed_info.txt", e);
        }
    }

    // Fares V1 legado (sección 20 del prompt: "mantener compatibilidad de importación
    // con Fares V1 cuando un feed existente lo utilice"). fare_attributes.txt se traduce
    // 1:1 a fare_product. fare_rules.txt en el Fares V1 clásico asocia cada fare_id a un
    // conjunto de route_id — como el esquema de Fase 1 todavía no modela networks/
    // route_networks (Fase 2), solo se traduce fielmente el caso común de una tarifa que
    // cubre TODA la red (fare_leg_rule con network_id nulo, equivalente a Fares V2); si el
    // fare_rules.txt real tiene scoping más fino (subconjunto de rutas, o zonas vía
    // origin_id/destination_id/contains_id) se avisa en vez de traducirlo en silencio,
    // para no fingir una cobertura que el feed original no tiene (sección 28: "no
    // modificar silenciosamente el contenido importado").
    private void importFaresV1(Path dir, FeedVersion fv, Map<String, Route> routes, Map<String, Integer> counts,
                                List<String> warnings) {
        Map<String, FareProduct> productByFareId = new LinkedHashMap<>();
        try (CSVParser parser = openCsv(dir, "fare_attributes.txt")) {
            if (parser == null) {
                return;
            }
            int n = 0;
            for (CSVRecord r : parser) {
                String price = get(r, "price");
                if (price == null) {
                    warnings.add("fare_attributes.txt: fare_id=" + get(r, "fare_id") + " sin price, se omite");
                    continue;
                }
                FareProduct p = FareProduct.builder()
                        .feedVersion(fv)
                        .gtfsId(get(r, "fare_id"))
                        .fareProductName(get(r, "fare_id"))
                        .amount(new BigDecimal(price))
                        .currency(get(r, "currency_type") != null ? get(r, "currency_type") : "MXN")
                        .build();
                p = fareProductRepository.save(p);
                productByFareId.put(p.getGtfsId(), p);
                n++;
            }
            counts.put("fare_products_v1", n);
        } catch (IOException e) {
            throw new RuntimeException("Error leyendo fare_attributes.txt", e);
        }
        if (productByFareId.isEmpty()) {
            return;
        }

        try (CSVParser parser = openCsv(dir, "fare_rules.txt")) {
            if (parser == null) {
                // fare_attributes.txt sin fare_rules.txt: GTFS lo permite (tarifa implícita
                // de red completa) -> se traduce igual a network-wide.
                for (FareProduct product : productByFareId.values()) {
                    fareLegRuleRepository.save(FareLegRule.builder().feedVersion(fv).fareProduct(product).build());
                }
                counts.put("fare_leg_rules", productByFareId.size());
                return;
            }
            Map<String, Set<String>> routeIdsByFareId = new LinkedHashMap<>();
            boolean hasZoneScoping = false;
            for (CSVRecord r : parser) {
                String fareId = get(r, "fare_id");
                if (get(r, "origin_id") != null || get(r, "destination_id") != null || get(r, "contains_id") != null) {
                    hasZoneScoping = true;
                }
                routeIdsByFareId.computeIfAbsent(fareId, k -> new LinkedHashSet<>()).add(get(r, "route_id"));
            }
            if (hasZoneScoping) {
                warnings.add("fare_rules.txt: se detectó scoping por zona (origin_id/destination_id/contains_id); "
                        + "no se modela todavía en Fase 1, se ignoró esa granularidad");
            }
            int n = 0;
            for (Map.Entry<String, Set<String>> entry : routeIdsByFareId.entrySet()) {
                FareProduct product = productByFareId.get(entry.getKey());
                if (product == null) {
                    warnings.add("fare_rules.txt: fare_id=" + entry.getKey() + " no está en fare_attributes.txt, se omite");
                    continue;
                }
                boolean coversAllRoutes = entry.getValue().stream().allMatch(Objects::nonNull)
                        && entry.getValue().containsAll(routes.keySet());
                if (!coversAllRoutes) {
                    warnings.add("fare_rules.txt: fare_id=" + entry.getKey() + " no cubre todas las rutas del feed ("
                            + entry.getValue().size() + "/" + routes.size()
                            + "); el scoping por ruta individual aún no se modela en Fase 1, se importó como tarifa de red completa");
                }
                fareLegRuleRepository.save(FareLegRule.builder().feedVersion(fv).fareProduct(product).build());
                n++;
            }
            counts.put("fare_leg_rules", n);
        } catch (IOException e) {
            throw new RuntimeException("Error leyendo fare_rules.txt", e);
        }
    }

    private static String get(CSVRecord r, String column) {
        if (!r.isMapped(column)) {
            return null;
        }
        String v = r.get(column);
        return (v == null || v.isBlank()) ? null : v;
    }

    private static boolean bool(CSVRecord r, String column) {
        String v = get(r, column);
        return "1".equals(v);
    }

    private static Short shortOrDefault(CSVRecord r, String column, short def) {
        String v = get(r, column);
        return v == null ? def : Short.parseShort(v);
    }

    private static Integer intOrDefault(CSVRecord r, String column, int def) {
        String v = get(r, column);
        return v == null ? def : Integer.parseInt(v);
    }

    private static Integer intOrNull(CSVRecord r, String column) {
        String v = get(r, column);
        return v == null ? null : Integer.parseInt(v);
    }

    private static Double doubleOrNull(CSVRecord r, String column) {
        String v = get(r, column);
        return v == null ? null : Double.parseDouble(v);
    }
}
