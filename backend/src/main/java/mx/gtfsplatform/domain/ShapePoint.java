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
@Table(name = "shape_point")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShapePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "route_pattern_id", nullable = false)
    private RoutePattern routePattern;

    @Column(name = "shape_pt_sequence", nullable = false)
    private Integer shapePtSequence;

    @Column(name = "shape_pt_lat", nullable = false)
    private Double shapePtLat;

    @Column(name = "shape_pt_lon", nullable = false)
    private Double shapePtLon;

    @Column(name = "shape_dist_traveled")
    private Double shapeDistTraveled;
}
