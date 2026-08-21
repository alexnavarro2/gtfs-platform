package mx.gtfsplatform.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * arrivalTimeSec/departureTimeSec son segundos-desde-medianoche del service day
 * (no java.time.LocalTime), para soportar correctamente valores >= 24:00:00.
 */
@Entity
@Table(name = "stop_time")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StopTime {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pattern_stop_id")
    private PatternStop patternStop;

    @Column(name = "stop_sequence", nullable = false)
    private Integer stopSequence;

    @Column(name = "arrival_time_sec", nullable = false)
    private Integer arrivalTimeSec;

    @Column(name = "departure_time_sec", nullable = false)
    private Integer departureTimeSec;

    @Column(name = "stop_headsign")
    private String stopHeadsign;

    @Column(name = "pickup_type", nullable = false)
    private Short pickupType;

    @Column(name = "drop_off_type", nullable = false)
    private Short dropOffType;

    @Column(name = "shape_dist_traveled")
    private Double shapeDistTraveled;

    @Column(name = "timepoint", nullable = false)
    private Short timepoint;
}
