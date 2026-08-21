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
@Table(name = "pattern_stop")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatternStop {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "route_pattern_id", nullable = false)
    private RoutePattern routePattern;

    @ManyToOne
    @JoinColumn(name = "stop_id", nullable = false)
    private Stop stop;

    @Column(name = "stop_sequence", nullable = false)
    private Integer stopSequence;

    @Column(name = "distance_along_shape")
    private Double distanceAlongShape;

    @Column(name = "default_timepoint", nullable = false)
    private Short defaultTimepoint;

    @Column(name = "default_pickup_type", nullable = false)
    private Short defaultPickupType;

    @Column(name = "default_drop_off_type", nullable = false)
    private Short defaultDropOffType;

    @Column(name = "stop_headsign")
    private String stopHeadsign;
}
