package mx.gtfsplatform.repository;

import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.TransferRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRuleRepository extends JpaRepository<TransferRule, UUID> {
    List<TransferRule> findByFeedVersionId(UUID feedVersionId);

    List<TransferRule> findByFromStopId(UUID fromStopId);

    List<TransferRule> findByToStopId(UUID toStopId);
}
