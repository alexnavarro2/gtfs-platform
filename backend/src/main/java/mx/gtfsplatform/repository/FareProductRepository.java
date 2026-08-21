package mx.gtfsplatform.repository;

import java.util.List;
import java.util.UUID;
import mx.gtfsplatform.domain.FareProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FareProductRepository extends JpaRepository<FareProduct, UUID> {
    List<FareProduct> findByFeedVersionId(UUID feedVersionId);
}
