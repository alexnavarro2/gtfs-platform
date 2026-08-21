package mx.gtfsplatform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

@Entity
@Table(name = "stop")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stop {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "feed_version_id", nullable = false)
    private FeedVersion feedVersion;

    @Column(name = "gtfs_id", nullable = false)
    private String gtfsId;

    @Column(name = "stop_code")
    private String stopCode;

    @Column(name = "stop_name")
    private String stopName;

    @Column(name = "tts_stop_name")
    private String ttsStopName;

    @Column(name = "stop_desc")
    private String stopDesc;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "geom", nullable = false)
    private Point geom;

    @Column(name = "zone_id")
    private String zoneId;

    @Column(name = "stop_url")
    private String stopUrl;

    @Column(name = "location_type", nullable = false)
    private Short locationType;

    @ManyToOne
    @JoinColumn(name = "parent_station_id")
    private Stop parentStation;

    @Column(name = "stop_timezone")
    private String stopTimezone;

    @Column(name = "wheelchair_boarding", nullable = false)
    private Short wheelchairBoarding;

    @Column(name = "platform_code")
    private String platformCode;

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

    @Transient
    public Double getStopLat() {
        return geom != null ? geom.getY() : null;
    }

    @Transient
    public Double getStopLon() {
        return geom != null ? geom.getX() : null;
    }

    @Transient
    public void setStopLat(Double lat) {
        double lon = geom != null ? geom.getX() : 0d;
        this.geom = GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat != null ? lat : 0d));
    }

    @Transient
    public void setStopLon(Double lon) {
        double lat = geom != null ? geom.getY() : 0d;
        this.geom = GEOMETRY_FACTORY.createPoint(new Coordinate(lon != null ? lon : 0d, lat));
    }
}
