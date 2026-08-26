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
@Table(name = "kml_stop_import_job")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KmlStopImportJob {

    public enum Status {
        RUNNING, DONE, FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "feed_version_id", nullable = false)
    private FeedVersion feedVersion;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "total_points", nullable = false)
    private Integer totalPoints;

    @Column(name = "processed_count", nullable = false)
    private Integer processedCount;

    @Column(name = "geocoded_count", nullable = false)
    private Integer geocodedCount;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "min_lat")
    private Double minLat;

    @Column(name = "max_lat")
    private Double maxLat;

    @Column(name = "min_lon")
    private Double minLon;

    @Column(name = "max_lon")
    private Double maxLon;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;
}
