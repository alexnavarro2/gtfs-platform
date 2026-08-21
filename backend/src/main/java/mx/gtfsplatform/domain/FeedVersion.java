package mx.gtfsplatform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "feed_version")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "feed_id", nullable = false)
    private Feed feed;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FeedVersionStatus status;

    @Column(name = "feed_publisher_name")
    private String feedPublisherName;

    @Column(name = "feed_publisher_url")
    private String feedPublisherUrl;

    @Column(name = "feed_lang")
    private String feedLang;

    @Column(name = "default_lang")
    private String defaultLang;

    @Column(name = "feed_start_date")
    private LocalDate feedStartDate;

    @Column(name = "feed_end_date")
    private LocalDate feedEndDate;

    @Column(name = "feed_version_string")
    private String feedVersionString;

    @Column(name = "feed_contact_email")
    private String feedContactEmail;

    @Column(name = "feed_contact_url")
    private String feedContactUrl;

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
