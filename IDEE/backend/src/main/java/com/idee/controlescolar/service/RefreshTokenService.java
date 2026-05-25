package com.idee.controlescolar.service;

import com.idee.controlescolar.model.RefreshToken;
import com.idee.controlescolar.model.Usuario;
import com.idee.controlescolar.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UsuarioService usuarioService;

    @Value("${jwt.refresh-expiration:604800000}") // 7 días en ms por defecto
    private Long refreshExpirationMs;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public String createRefreshToken(Usuario usuario) {
        refreshTokenRepository.revokeAllByUsuarioId(usuario.getId());
        String tokenValue = generateSecureToken();
        String tokenHash = hashToken(tokenValue);
        Instant expiry = Instant.now().plusMillis(refreshExpirationMs);

        RefreshToken rt = new RefreshToken();
        rt.setUsuarioId(usuario.getId());
        rt.setTokenHash(tokenHash);
        rt.setExpiry(expiry);
        rt.setRevoked(false);
        rt.setCreatedAt(Instant.now());
        refreshTokenRepository.save(rt);
        return tokenValue;
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> validateAndGetUsuario(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) return Optional.empty();
        String tokenHash = hashToken(tokenValue);
        return refreshTokenRepository.findByTokenHashAndRevokedFalseAndExpiryAfter(tokenHash, Instant.now())
                .map(rt -> usuarioService.findById(rt.getUsuarioId()))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    @Transactional
    public void revokeByToken(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) return;
        String tokenHash = hashToken(tokenValue);
        refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .ifPresent(rt -> {
                    rt.setRevoked(true);
                    refreshTokenRepository.save(rt);
                });
    }

    @Transactional
    public void revokeAllForUser(Long usuarioId) {
        refreshTokenRepository.revokeAllByUsuarioId(usuarioId);
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error hashing token", e);
        }
    }
}
