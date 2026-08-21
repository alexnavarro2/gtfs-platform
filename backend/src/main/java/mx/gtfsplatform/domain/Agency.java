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
@Table(name = "agency")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agency {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "feed_version_id", nullable = false)
    private FeedVersion feedVersion;

    @Column(name = "gtfs_id", nullable = false)
    private String gtfsId;

    @Column(name = "agency_name", nullable = false)
    private String agencyName;

    @Column(name = "agency_url", nullable = false)
    private String agencyUrl;

    @Column(name = "agency_timezone", nullable = false)
    private String agencyTimezone;

    @Column(name = "agency_lang")
    private String agencyLang;

    @Column(name = "agency_phone")
    private String agencyPhone;

    @Column(name = "agency_fare_url")
    private String agencyFareUrl;

    @Column(name = "agency_email")
    private String agencyEmail;

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
