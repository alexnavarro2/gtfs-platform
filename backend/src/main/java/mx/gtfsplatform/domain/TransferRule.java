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
@Table(name = "transfer_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "feed_version_id", nullable = false)
    private FeedVersion feedVersion;

    @ManyToOne
    @JoinColumn(name = "from_stop_id")
    private Stop fromStop;

    @ManyToOne
    @JoinColumn(name = "to_stop_id")
    private Stop toStop;

    @Column(name = "transfer_type", nullable = false)
    private Short transferType;

    @Column(name = "min_transfer_time_sec")
    private Integer minTransferTimeSec;
}
