package mx.gtfsplatform.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import mx.gtfsplatform.domain.AppUser;
import mx.gtfsplatform.repository.AppUserRepository;
import mx.gtfsplatform.security.CurrentUser;
import mx.gtfsplatform.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (appUserRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyRegisteredException("Ya existe una cuenta con ese correo");
        }
        // El primer usuario de una instalación nueva no tiene quién le dé permisos
        // de ADMIN desde el panel — se auto-asigna para poder arrancar el panel de
        // administración; todos los siguientes entran como EDITOR.
        String role = appUserRepository.count() == 0 ? "ADMIN" : "EDITOR";
        AppUser user = AppUser.builder()
                .email(email)
                .displayName(request.displayName().trim())
                .institution(request.institution().trim())
                .jobTitle(request.jobTitle().trim())
                .role(role)
                .passwordHash(passwordEncoder.encode(request.password()))
                .createdAt(OffsetDateTime.now())
                .build();
        appUserRepository.save(user);
        return new AuthResponse(jwtService.generateToken(user), UserView.of(user));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        AppUser user = appUserRepository.findByEmail(request.email().trim().toLowerCase())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return new AuthResponse(jwtService.generateToken(user), UserView.of(user));
    }

    @GetMapping("/me")
    public UserView me() {
        return UserView.of(CurrentUser.get());
    }

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres") String password,
            @NotBlank String displayName,
            @NotBlank String institution,
            @NotBlank String jobTitle) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record UserView(
            String id, String email, String displayName, String institution, String jobTitle, String role) {
        static UserView of(AppUser user) {
            return new UserView(
                    user.getId().toString(),
                    user.getEmail(),
                    user.getDisplayName(),
                    user.getInstitution(),
                    user.getJobTitle(),
                    user.getRole());
        }
    }

    public record AuthResponse(String token, UserView user) {
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class EmailAlreadyRegisteredException extends RuntimeException {
        public EmailAlreadyRegisteredException(String message) {
            super(message);
        }
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Correo o contraseña incorrectos");
        }
    }
}
