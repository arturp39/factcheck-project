package com.factcheck.backend.security;

import com.factcheck.backend.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private SecurityProperties buildProps() {
        SecurityProperties props = new SecurityProperties();
        props.setJwtSecret("0123456789abcdef0123456789abcdef");
        props.setJwtIssuer("issuer");
        props.setJwtTtlMinutes(60);
        return props;
    }

    private String buildToken(SecurityProperties props, String subject, String issuer, Object roles) {
        SecretKey key = Keys.hmacShaKeyFor(props.getJwtSecret().getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(key, Jwts.SIG.HS256);
        if (roles != null) {
            builder.claim("roles", roles);
        }
        return builder.compact();
    }

    private void invokeValidateClaims(JwtService service, Claims claims) {
        try {
            Method method = JwtService.class.getDeclaredMethod("validateClaims", Claims.class);
            method.setAccessible(true);
            method.invoke(service, claims);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    void constructor_rejectsShortSecret() {
        SecurityProperties props = new SecurityProperties();
        props.setJwtSecret("short");

        assertThrows(IllegalArgumentException.class, () -> new JwtService(props));
    }

    @Test
    void generateAndParseToken_roundTripsClaims() {
        SecurityProperties props = buildProps();
        JwtService service = new JwtService(props);

        String token = service.generateToken("user", List.of("USER", "ADMIN"));
        Claims claims = service.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("user");
        assertThat(claims.getIssuer()).isEqualTo("issuer");
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) claims.get("roles");
        assertThat(roles).contains("USER", "ADMIN");
    }

    @Test
    void parseClaims_rejectsWrongIssuer() {
        SecurityProperties props = buildProps();
        JwtService service = new JwtService(props);
        String token = buildToken(props, "user", "other", List.of("USER"));

        assertThrows(JwtException.class, () -> service.parseClaims(token));
    }

    @Test
    void parseClaims_rejectsBlankSubject() {
        SecurityProperties props = buildProps();
        JwtService service = new JwtService(props);
        String token = buildToken(props, " ", props.getJwtIssuer(), List.of("USER"));

        assertThrows(JwtException.class, () -> service.parseClaims(token));
    }

    @Test
    void parseClaims_rejectsMissingRoles() {
        SecurityProperties props = buildProps();
        JwtService service = new JwtService(props);
        String token = buildToken(props, "user", props.getJwtIssuer(), List.of(" ", ""));

        assertThrows(JwtException.class, () -> service.parseClaims(token));
    }

    @Test
    void parseClaims_rejectsNullRoles() {
        SecurityProperties props = buildProps();
        JwtService service = new JwtService(props);
        String token = buildToken(props, "user", props.getJwtIssuer(), null);

        assertThrows(JwtException.class, () -> service.parseClaims(token));
    }

    @Test
    void validateClaims_rejectsNullClaims() {
        SecurityProperties props = buildProps();
        JwtService service = new JwtService(props);

        assertThrows(JwtException.class, () -> invokeValidateClaims(service, null));
    }

    @Test
    void parseClaims_acceptsStringRole() {
        SecurityProperties props = buildProps();
        JwtService service = new JwtService(props);
        String token = buildToken(props, "user", props.getJwtIssuer(), "ADMIN");

        Claims claims = service.parseClaims(token);

        assertThat(claims.get("roles")).isEqualTo("ADMIN");
    }

    @Test
    void parseClaims_acceptsNonStringRoleObject() {
        SecurityProperties props = buildProps();
        JwtService service = new JwtService(props);
        String token = buildToken(props, "user", props.getJwtIssuer(), 1);

        Claims claims = service.parseClaims(token);

        assertThat(claims.get("roles")).isEqualTo(1);
    }
}
