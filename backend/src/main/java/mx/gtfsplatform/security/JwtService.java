package mx.gtfsplatform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import mx.gtfsplatform.config.GtfsPlatformProperties;
import mx.gtfsplatform.domain.AppUser;
import org.springframework.stereotype.Component;

@Component
public class JwtService {

    private final SecretKey key;
    private final int expirationHours;

    public JwtService(GtfsPlatformProperties properties) {
        this.key = Keys.hmacShaKeyFor(
                properties.getSecurity().getJwtSecret().getBytes(StandardCharsets.UTF_8));
        this.expirationHours = properties.getSecurity().getJwtExpirationHours();
    }

    public String generateToken(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationHours * 3600L)))
                .signWith(key)
                .compact();
    }

    public UUID parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return UUID.fromString(claims.getSubject());
    }
}
