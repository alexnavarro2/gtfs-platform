package mx.gtfsplatform.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trip")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "route_pattern_id")
    private RoutePattern routePattern;

    @ManyToOne(optional = false)
    @JoinColumn(name = "service_calendar_id")
    private ServiceCalendar serviceCalendar;

    @Column(name = "gtfs_id", nullable = false)
    private String gtfsId;

    @Column(name = "trip_headsign")
    private String tripHeadsign;

    @Column(name = "trip_short_name")
    private String tripShortName;

    @Column(name = "block_id")
    private String blockId;

    @Column(name = "wheelchair_accessible", nullable = false)
    private Short wheelchairAccessible;

    @Column(name = "bikes_allowed", nullable = false)
    private Short bikesAllowed;

    @Column(name = "is_frequency_based", nullable = false)
    private Boolean frequencyBased;

    @Column(name = "created_at", updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false)
    private Instant updatedAt;
}
