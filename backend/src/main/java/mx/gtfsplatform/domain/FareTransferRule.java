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
@Table(name = "fare_transfer_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FareTransferRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "feed_version_id", nullable = false)
    private FeedVersion feedVersion;

    @Column(name = "from_leg_group_id")
    private String fromLegGroupId;

    @Column(name = "to_leg_group_id")
    private String toLegGroupId;

    @Column(name = "transfer_count")
    private Short transferCount;

    @Column(name = "duration_limit_secs")
    private Integer durationLimitSecs;

    @Column(name = "duration_limit_type")
    private Short durationLimitType;

    @Column(name = "fare_transfer_type", nullable = false)
    private Short fareTransferType;

    @ManyToOne
    @JoinColumn(name = "fare_product_id")
    private FareProduct fareProduct;
}
