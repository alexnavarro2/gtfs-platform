package mx.gtfsplatform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fare_product")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FareProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "feed_version_id", nullable = false)
    private FeedVersion feedVersion;

    @Column(name = "gtfs_id", nullable = false)
    private String gtfsId;

    @Column(name = "fare_product_name", nullable = false)
    private String fareProductName;

    @ManyToOne
    @JoinColumn(name = "rider_category_id")
    private RiderCategory riderCategory;

    @ManyToOne
    @JoinColumn(name = "fare_media_id")
    private FareMedia fareMedia;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency;
}
