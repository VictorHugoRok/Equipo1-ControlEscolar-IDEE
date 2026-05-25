package com.idee.controlescolar.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
@Slf4j
public class JwtUtil {

    private final Environment environment;

    @Value("${jwt.secret:}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private Long expiration;

    public JwtUtil(Environment environment) {
        this.environment = environment;
    }

    /** Mismo default que application.properties; prohibido en perfil production. */
    private static final String WEAK_DEFAULT_JWT_SECRET =
            "9e25e2qe8r9e2q8r9e2q8r9e2q8r9e2q8r9e2q8r9e2q8r9e2q8r9e2q8r9e2q8r";

    @PostConstruct
    void validateJwtSecretStrength() {
        boolean production = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "production".equalsIgnoreCase(p));
        if (secret == null || secret.isBlank()) {
            if (production) {
                throw new IllegalStateException(
                        "En producción defina JWT_SECRET (variable de entorno); jwt.secret no puede estar vacío.");
            }
            secret = WEAK_DEFAULT_JWT_SECRET;
            log.warn(
                    "JWT_SECRET no definido; usando secreto solo para desarrollo. Defina JWT_SECRET en .env o en el entorno.");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (production) {
            if (secretBytes.length < 32) {
                throw new IllegalStateException(
                        "En perfil production JWT_SECRET debe tener al menos 32 bytes (p. ej. openssl rand -base64 48).");
            }
            if (WEAK_DEFAULT_JWT_SECRET.equals(secret)) {
                throw new IllegalStateException(
                        "En producción defina JWT_SECRET en variables de entorno; no use el valor por defecto del repositorio.");
            }
        } else if (secretBytes.length < 32) {
            log.warn("jwt.secret es corto ({} bytes). Use al menos 32 bytes para HMAC-SHA256.", secretBytes.length);
        }
    }

    /** Duración del access token en segundos (para enviar al frontend como expiresIn). */
    public long getExpirationSeconds() {
        return expiration != null ? expiration / 1000 : 86400;
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ========================
    // EXTRACCIÓN DE DATOS
    // ========================

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = extractAllClaims(token);
        return resolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ========================
    // GENERACIÓN DE TOKEN
    // ========================

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, userDetails.getUsername());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    // ========================
    // VALIDACIÓN
    // ========================

    public boolean validateToken(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }
}
