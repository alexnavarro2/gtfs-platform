package mx.gtfsplatform.repository;

import mx.gtfsplatform.domain.FrequencyEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FrequencyEntryRepository extends JpaRepository<FrequencyEntry, UUID> {
    List<FrequencyEntry> findByTripId(UUID tripId);
    List<FrequencyEntry> findByTripIdIn(List<UUID> tripIds);
}
