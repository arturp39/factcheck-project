package com.factcheck.backend.security;

import com.factcheck.backend.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    private final SecurityProperties securityProperties;
    private final SecretKey secretKey;

    public JwtService(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
        if (securityProperties.getJwtSecret() == null || securityProperties.getJwtSecret().length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters.");
        }
        this.secretKey = Keys.hmacShaKeyFor(securityProperties.getJwtSecret()
                .getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username, List<String> roles) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(securityProperties.getJwtTtlMinutes() * 60);

        return Jwts.builder()
                .subject(username)
                .issuer(securityProperties.getJwtIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("roles", roles)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parseClaims(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        validateClaims(claims);
        return claims;
    }

    private void validateClaims(Claims claims) {
        if (claims == null) {
            throw new JwtException("Missing JWT claims.");
        }
        String issuer = claims.getIssuer();
        if (issuer == null || !issuer.equals(securityProperties.getJwtIssuer())) {
            throw new JwtException("Invalid JWT issuer.");
        }
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new JwtException("Invalid JWT subject.");
        }
        Object roles = claims.get("roles");
        if (!hasRoles(roles)) {
            throw new JwtException("JWT roles are missing.");
        }
    }

    private boolean hasRoles(Object roles) {
        if (roles == null) {
            return false;
        }
        if (roles instanceof Iterable<?> iterable) {
            for (Object role : iterable) {
                if (role != null && !role.toString().isBlank()) {
                    return true;
                }
            }
            return false;
        }
        if (roles instanceof String roleStr) {
            return !roleStr.isBlank();
        }
        return true;
    }
}
