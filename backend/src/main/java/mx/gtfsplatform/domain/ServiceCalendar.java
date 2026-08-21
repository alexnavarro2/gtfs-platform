package mx.gtfsplatform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "service_calendar")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceCalendar {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "feed_version_id", nullable = false)
    private FeedVersion feedVersion;

    @Column(name = "gtfs_id", nullable = false)
    private String gtfsId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "monday", nullable = false)
    private Boolean monday;

    @Column(name = "tuesday", nullable = false)
    private Boolean tuesday;

    @Column(name = "wednesday", nullable = false)
    private Boolean wednesday;

    @Column(name = "thursday", nullable = false)
    private Boolean thursday;

    @Column(name = "friday", nullable = false)
    private Boolean friday;

    @Column(name = "saturday", nullable = false)
    private Boolean saturday;

    @Column(name = "sunday", nullable = false)
    private Boolean sunday;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
