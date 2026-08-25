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

@Entity
@Table(name = "route")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "feed_version_id", nullable = false)
    private FeedVersion feedVersion;

    @Column(name = "gtfs_id", nullable = false)
    private String gtfsId;

    @ManyToOne
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @Column(name = "route_short_name")
    private String routeShortName;

    @Column(name = "route_long_name")
    private String routeLongName;

    @Column(name = "route_desc")
    private String routeDesc;

    @Column(name = "route_type", nullable = false)
    private Integer routeType;

    @Column(name = "route_url")
    private String routeUrl;

    @Column(name = "route_color")
    private String routeColor;

    @Column(name = "route_text_color")
    private String routeTextColor;

    @Column(name = "route_sort_order")
    private Integer routeSortOrder;

    @Column(name = "continuous_pickup")
    private Short continuousPickup;

    @Column(name = "continuous_drop_off")
    private Short continuousDropOff;

    // Solo se usa para referenciar la ruta desde fare_leg_rules.txt (Fares V2) —
    // ver V6__route_network_id.sql. No confundir con "route.network_id" del
    // esquema legado: es literalmente el mismo campo, opcional, para esa función.
    @Column(name = "network_id")
    private String networkId;

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
