package mx.gtfsplatform.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "export_artifact")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportArtifact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "feed_version_id")
    private FeedVersion feedVersion;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "sha256", nullable = false)
    private String sha256;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;
}
