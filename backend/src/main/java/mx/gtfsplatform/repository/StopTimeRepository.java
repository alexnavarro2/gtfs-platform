package mx.gtfsplatform.repository;

import mx.gtfsplatform.domain.StopTime;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StopTimeRepository extends JpaRepository<StopTime, UUID> {
    List<StopTime> findByTripIdOrderByStopSequenceAsc(UUID tripId);
    List<StopTime> findByTripIdIn(List<UUID> tripIds);
    void deleteByTripId(UUID tripId);
}
