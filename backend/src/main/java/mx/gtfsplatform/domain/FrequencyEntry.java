package mx.gtfsplatform.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "frequency_entry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FrequencyEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Column(name = "start_time_sec", nullable = false)
    private Integer startTimeSec;

    @Column(name = "end_time_sec", nullable = false)
    private Integer endTimeSec;

    @Column(name = "headway_secs", nullable = false)
    private Integer headwaySecs;

    @Column(name = "exact_times", nullable = false)
    private Short exactTimes;
}
