package com.idee.controlescolar.repository;

import com.idee.controlescolar.model.RefreshToken;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHashAndRevokedFalseAndExpiryAfter(String tokenHash, Instant now);

    /** Para revocar sin filtrar por caducidad (no usar Instant.MIN: PostgreSQL no admite ese rango). */
    Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.usuarioId = :usuarioId")
    void revokeAllByUsuarioId(@Param("usuarioId") Long usuarioId);
}
