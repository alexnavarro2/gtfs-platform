package mx.gtfsplatform.repository;

import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.FareLegRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FareLegRuleRepository extends JpaRepository<FareLegRule, UUID> {
    List<FareLegRule> findByFeedVersionId(UUID feedVersionId);

    List<FareLegRule> findByFareProductId(UUID fareProductId);
}
