package mx.gtfsplatform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fare_leg_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FareLegRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "feed_version_id", nullable = false)
    private FeedVersion feedVersion;

    @Column(name = "gtfs_leg_group_id")
    private String gtfsLegGroupId;

    @Column(name = "network_id")
    private String networkId;

    @ManyToOne
    @JoinColumn(name = "fare_product_id", nullable = false)
    private FareProduct fareProduct;
}
