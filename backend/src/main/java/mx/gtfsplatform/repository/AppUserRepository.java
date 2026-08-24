package mx.gtfsplatform.repository;

import java.util.Optional;
import java.util.UUID;
import mx.gtfsplatform.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByEmail(String email);
}
