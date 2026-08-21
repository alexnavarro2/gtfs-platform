package mx.gtfsplatform.repository;

import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.FareTransferRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FareTransferRuleRepository extends JpaRepository<FareTransferRule, UUID> {
    List<FareTransferRule> findByFeedVersionId(UUID feedVersionId);

    List<FareTransferRule> findByFareProductId(UUID fareProductId);
}
