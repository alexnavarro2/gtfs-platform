package mx.gtfsplatform.web;

import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.FareLegRule;
import mx.gtfsplatform.domain.FareMedia;
import mx.gtfsplatform.domain.FareProduct;
import mx.gtfsplatform.domain.FareTransferRule;
import mx.gtfsplatform.domain.FeedVersion;
import mx.gtfsplatform.domain.RiderCategory;
import mx.gtfsplatform.gtfs.GtfsIdGenerator;
import mx.gtfsplatform.repository.FareLegRuleRepository;
import mx.gtfsplatform.repository.FareMediaRepository;
import mx.gtfsplatform.repository.FareProductRepository;
import mx.gtfsplatform.repository.FareTransferRuleRepository;
import mx.gtfsplatform.repository.FeedVersionRepository;
import mx.gtfsplatform.repository.RiderCategoryRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/feed-versions/{feedVersionId}")
public class FareController {

    private final RiderCategoryRepository riderCategoryRepository;
    private final FareMediaRepository fareMediaRepository;
    private final FareProductRepository fareProductRepository;
    private final FareLegRuleRepository fareLegRuleRepository;
    private final FareTransferRuleRepository fareTransferRuleRepository;
    private final FeedVersionRepository feedVersionRepository;
    private final GtfsIdGenerator idGenerator;

    public FareController(RiderCategoryRepository riderCategoryRepository, FareMediaRepository fareMediaRepository,
            FareProductRepository fareProductRepository, FareLegRuleRepository fareLegRuleRepository,
            FareTransferRuleRepository fareTransferRuleRepository, FeedVersionRepository feedVersionRepository,
            GtfsIdGenerator idGenerator) {
        this.riderCategoryRepository = riderCategoryRepository;
        this.fareMediaRepository = fareMediaRepository;
        this.fareProductRepository = fareProductRepository;
        this.fareLegRuleRepository = fareLegRuleRepository;
        this.fareTransferRuleRepository = fareTransferRuleRepository;
        this.feedVersionRepository = feedVersionRepository;
        this.idGenerator = idGenerator;
    }

    private FeedVersion requireFeedVersion(UUID feedVersionId) {
        return feedVersionRepository.findById(feedVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("FeedVersion not found: " + feedVersionId));
    }

    // ---- rider-categories ----

    @GetMapping("/rider-categories")
    public List<RiderCategory> listRiderCategories(@PathVariable UUID feedVersionId) {
        return riderCategoryRepository.findByFeedVersionId(feedVersionId);
    }

    @PostMapping("/rider-categories")
    public RiderCategory createRiderCategory(@PathVariable UUID feedVersionId, @RequestBody RiderCategory entity) {
        entity.setId(null);
        entity.setFeedVersion(requireFeedVersion(feedVersionId));
        if (entity.getGtfsId() == null || entity.getGtfsId().isBlank()) {
            entity.setGtfsId(idGenerator.next("rider_category", "RIDERCAT", feedVersionId, "feed_version_id"));
        }
        return riderCategoryRepository.save(entity);
    }

    @PutMapping("/rider-categories/{id}")
    public RiderCategory updateRiderCategory(@PathVariable UUID feedVersionId, @PathVariable UUID id,
            @RequestBody RiderCategory update) {
        RiderCategory existing = riderCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RiderCategory not found: " + id));
        // gtfs_id/rider_category_name/is_default_fare_category son NOT NULL — mismo
        // patrón null-safe que AgencyController/RouteController, para que un
        // formulario que edite un solo campo no tumbe los demás a NULL.
        if (update.getGtfsId() != null && !update.getGtfsId().isBlank()) {
            existing.setGtfsId(update.getGtfsId());
        }
        if (update.getRiderCategoryName() != null) {
            existing.setRiderCategoryName(update.getRiderCategoryName());
        }
        if (update.getIsDefaultFareCategory() != null) {
            existing.setIsDefaultFareCategory(update.getIsDefaultFareCategory());
        }
        return riderCategoryRepository.save(existing);
    }

    @DeleteMapping("/rider-categories/{id}")
    public void deleteRiderCategory(@PathVariable UUID feedVersionId, @PathVariable UUID id) {
        riderCategoryRepository.deleteById(id);
    }

    // ---- fare-media ----

    @GetMapping("/fare-media")
    public List<FareMedia> listFareMedia(@PathVariable UUID feedVersionId) {
        return fareMediaRepository.findByFeedVersionId(feedVersionId);
    }

    @PostMapping("/fare-media")
    public FareMedia createFareMedia(@PathVariable UUID feedVersionId, @RequestBody FareMedia entity) {
        entity.setId(null);
        entity.setFeedVersion(requireFeedVersion(feedVersionId));
        if (entity.getGtfsId() == null || entity.getGtfsId().isBlank()) {
            entity.setGtfsId(idGenerator.next("fare_media", "FAREMEDIA", feedVersionId, "feed_version_id"));
        }
        return fareMediaRepository.save(entity);
    }

    @PutMapping("/fare-media/{id}")
    public FareMedia updateFareMedia(@PathVariable UUID feedVersionId, @PathVariable UUID id,
            @RequestBody FareMedia update) {
        FareMedia existing = fareMediaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FareMedia not found: " + id));
        if (update.getGtfsId() != null && !update.getGtfsId().isBlank()) {
            existing.setGtfsId(update.getGtfsId());
        }
        existing.setFareMediaName(update.getFareMediaName());
        if (update.getFareMediaType() != null) {
            existing.setFareMediaType(update.getFareMediaType());
        }
        return fareMediaRepository.save(existing);
    }

    @DeleteMapping("/fare-media/{id}")
    public void deleteFareMedia(@PathVariable UUID feedVersionId, @PathVariable UUID id) {
        fareMediaRepository.deleteById(id);
    }

    // ---- fare-products ----

    @GetMapping("/fare-products")
    public List<FareProduct> listFareProducts(@PathVariable UUID feedVersionId) {
        return fareProductRepository.findByFeedVersionId(feedVersionId);
    }

    @PostMapping("/fare-products")
    public FareProduct createFareProduct(@PathVariable UUID feedVersionId, @RequestBody FareProduct entity) {
        entity.setId(null);
        entity.setFeedVersion(requireFeedVersion(feedVersionId));
        if (entity.getGtfsId() == null || entity.getGtfsId().isBlank()) {
            entity.setGtfsId(idGenerator.next("fare_product", "FAREPROD", feedVersionId, "feed_version_id"));
        }
        return fareProductRepository.save(entity);
    }

    @PutMapping("/fare-products/{id}")
    public FareProduct updateFareProduct(@PathVariable UUID feedVersionId, @PathVariable UUID id,
            @RequestBody FareProduct update) {
        FareProduct existing = fareProductRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FareProduct not found: " + id));
        if (update.getGtfsId() != null && !update.getGtfsId().isBlank()) {
            existing.setGtfsId(update.getGtfsId());
        }
        if (update.getFareProductName() != null) {
            existing.setFareProductName(update.getFareProductName());
        }
        // rider_category/fare_media sí son opcionales de verdad (se puede querer
        // "sin categoría" o "sin medio"), así que se aceptan tal cual venga,
        // incluido null para desasociar.
        existing.setRiderCategory(update.getRiderCategory());
        existing.setFareMedia(update.getFareMedia());
        if (update.getAmount() != null) {
            existing.setAmount(update.getAmount());
        }
        if (update.getCurrency() != null) {
            existing.setCurrency(update.getCurrency());
        }
        return fareProductRepository.save(existing);
    }

    @DeleteMapping("/fare-products/{id}")
    public void deleteFareProduct(@PathVariable UUID feedVersionId, @PathVariable UUID id) {
        fareProductRepository.deleteById(id);
    }

    // ---- fare-leg-rules ----

    @GetMapping("/fare-leg-rules")
    public List<FareLegRule> listFareLegRules(@PathVariable UUID feedVersionId) {
        return fareLegRuleRepository.findByFeedVersionId(feedVersionId);
    }

    @PostMapping("/fare-leg-rules")
    public FareLegRule createFareLegRule(@PathVariable UUID feedVersionId, @RequestBody FareLegRule entity) {
        entity.setId(null);
        entity.setFeedVersion(requireFeedVersion(feedVersionId));
        return fareLegRuleRepository.save(entity);
    }

    @PutMapping("/fare-leg-rules/{id}")
    public FareLegRule updateFareLegRule(@PathVariable UUID feedVersionId, @PathVariable UUID id,
            @RequestBody FareLegRule update) {
        FareLegRule existing = fareLegRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FareLegRule not found: " + id));
        existing.setGtfsLegGroupId(update.getGtfsLegGroupId());
        existing.setNetworkId(update.getNetworkId());
        // fare_product_id es NOT NULL.
        if (update.getFareProduct() != null) {
            existing.setFareProduct(update.getFareProduct());
        }
        return fareLegRuleRepository.save(existing);
    }

    @DeleteMapping("/fare-leg-rules/{id}")
    public void deleteFareLegRule(@PathVariable UUID feedVersionId, @PathVariable UUID id) {
        fareLegRuleRepository.deleteById(id);
    }

    // ---- fare-transfer-rules ----

    @GetMapping("/fare-transfer-rules")
    public List<FareTransferRule> listFareTransferRules(@PathVariable UUID feedVersionId) {
        return fareTransferRuleRepository.findByFeedVersionId(feedVersionId);
    }

    @PostMapping("/fare-transfer-rules")
    public FareTransferRule createFareTransferRule(@PathVariable UUID feedVersionId,
            @RequestBody FareTransferRule entity) {
        entity.setId(null);
        entity.setFeedVersion(requireFeedVersion(feedVersionId));
        return fareTransferRuleRepository.save(entity);
    }

    @PutMapping("/fare-transfer-rules/{id}")
    public FareTransferRule updateFareTransferRule(@PathVariable UUID feedVersionId, @PathVariable UUID id,
            @RequestBody FareTransferRule update) {
        FareTransferRule existing = fareTransferRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FareTransferRule not found: " + id));
        existing.setFromLegGroupId(update.getFromLegGroupId());
        existing.setToLegGroupId(update.getToLegGroupId());
        existing.setTransferCount(update.getTransferCount());
        existing.setDurationLimitSecs(update.getDurationLimitSecs());
        existing.setDurationLimitType(update.getDurationLimitType());
        // fare_transfer_type es NOT NULL (default 0).
        if (update.getFareTransferType() != null) {
            existing.setFareTransferType(update.getFareTransferType());
        }
        existing.setFareProduct(update.getFareProduct());
        return fareTransferRuleRepository.save(existing);
    }

    @DeleteMapping("/fare-transfer-rules/{id}")
    public void deleteFareTransferRule(@PathVariable UUID feedVersionId, @PathVariable UUID id) {
        fareTransferRuleRepository.deleteById(id);
    }
}
