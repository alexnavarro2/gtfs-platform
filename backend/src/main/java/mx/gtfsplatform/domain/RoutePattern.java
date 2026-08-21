package mx.gtfsplatform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.LineString;

@Entity
@Table(name = "route_pattern")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoutePattern {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @Column(name = "shape_gtfs_id", nullable = false)
    private String shapeGtfsId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "direction_id", nullable = false)
    private Short directionId;

    @Column(name = "trip_headsign")
    private String tripHeadsign;

    @Column(name = "trip_short_name")
    private String tripShortName;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "geom")
    private LineString geom;

    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "updated_by")
    private AppUser updatedBy;
}
