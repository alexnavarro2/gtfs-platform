package mx.gtfsplatform.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mx.gtfsplatform.domain.AppUser;
import mx.gtfsplatform.repository.AppUserRepository;
import mx.gtfsplatform.repository.FeedRepository;
import mx.gtfsplatform.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Panel de administración: quién está registrado y con qué rol/permiso opera.
// Todo el controlador exige rol ADMIN (mismo patrón manual que FeedController,
// sin @PreAuthorize para no mezclar dos estilos de autorización en el proyecto).
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private static final Set<String> VALID_ROLES = Set.of("ADMIN", "EDITOR", "VIEWER");

    private final AppUserRepository appUserRepository;
    private final FeedRepository feedRepository;

    public AdminController(AppUserRepository appUserRepository, FeedRepository feedRepository) {
        this.appUserRepository = appUserRepository;
        this.feedRepository = feedRepository;
    }

    @GetMapping("/users")
    public List<AdminUserView> listUsers() {
        requireAdmin();
        return appUserRepository.findAll().stream()
                .sorted(Comparator.comparing(AppUser::getCreatedAt))
                .map(u -> new AdminUserView(
                        u.getId().toString(),
                        u.getEmail(),
                        u.getDisplayName(),
                        u.getInstitution(),
                        u.getJobTitle(),
                        u.getRole(),
                        u.getCreatedAt(),
                        feedRepository.findByCreatedBy_Id(u.getId()).size()))
                .toList();
    }

    @PutMapping("/users/{id}/role")
    public AdminUserView updateRole(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest request) {
        AppUser admin = requireAdmin();
        if (!VALID_ROLES.contains(request.role())) {
            throw new IllegalArgumentException("Rol inválido: " + request.role());
        }
        AppUser target = appUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + id));
        if (target.getId().equals(admin.getId()) && !"ADMIN".equals(request.role())) {
            throw new IllegalArgumentException("No puedes quitarte el rol de ADMIN a ti mismo");
        }
        target.setRole(request.role());
        appUserRepository.save(target);
        return new AdminUserView(
                target.getId().toString(),
                target.getEmail(),
                target.getDisplayName(),
                target.getInstitution(),
                target.getJobTitle(),
                target.getRole(),
                target.getCreatedAt(),
                feedRepository.findByCreatedBy_Id(target.getId()).size());
    }

    private AppUser requireAdmin() {
        AppUser user = CurrentUser.get();
        if (!"ADMIN".equals(user.getRole())) {
            throw new ForbiddenException("Requiere rol ADMIN");
        }
        return user;
    }

    public record AdminUserView(
            String id,
            String email,
            String displayName,
            String institution,
            String jobTitle,
            String role,
            java.time.OffsetDateTime createdAt,
            int feedCount) {
    }

    public record UpdateRoleRequest(@NotBlank @Pattern(regexp = "ADMIN|EDITOR|VIEWER") String role) {
    }
}
