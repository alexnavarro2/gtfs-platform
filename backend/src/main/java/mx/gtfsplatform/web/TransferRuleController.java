package mx.gtfsplatform.web;

import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.FeedVersion;
import mx.gtfsplatform.domain.TransferRule;
import mx.gtfsplatform.repository.FeedVersionRepository;
import mx.gtfsplatform.repository.TransferRuleRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransferRuleController {

    private final TransferRuleRepository transferRuleRepository;
    private final FeedVersionRepository feedVersionRepository;

    public TransferRuleController(TransferRuleRepository transferRuleRepository,
            FeedVersionRepository feedVersionRepository) {
        this.transferRuleRepository = transferRuleRepository;
        this.feedVersionRepository = feedVersionRepository;
    }

    @GetMapping("/api/v1/feed-versions/{feedVersionId}/transfer-rules")
    public List<TransferRule> list(@PathVariable UUID feedVersionId) {
        return transferRuleRepository.findByFeedVersionId(feedVersionId);
    }

    @PostMapping("/api/v1/feed-versions/{feedVersionId}/transfer-rules")
    public TransferRule create(@PathVariable UUID feedVersionId, @RequestBody TransferRule entity) {
        FeedVersion feedVersion = feedVersionRepository.findById(feedVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("FeedVersion not found: " + feedVersionId));
        entity.setId(null);
        entity.setFeedVersion(feedVersion);
        return transferRuleRepository.save(entity);
    }

    @PutMapping("/api/v1/transfer-rules/{id}")
    public TransferRule update(@PathVariable UUID id, @RequestBody TransferRule update) {
        TransferRule existing = transferRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TransferRule not found: " + id));
        existing.setFromStop(update.getFromStop());
        existing.setToStop(update.getToStop());
        existing.setTransferType(update.getTransferType());
        existing.setMinTransferTimeSec(update.getMinTransferTimeSec());
        return transferRuleRepository.save(existing);
    }

    @DeleteMapping("/api/v1/transfer-rules/{id}")
    public void delete(@PathVariable UUID id) {
        transferRuleRepository.deleteById(id);
    }
}
