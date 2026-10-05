package com.school.repository;

import com.school.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    void deleteByUserId(Long userId);

    void deleteByRevokedTrue();

    /**
     * Supprime les jetons révoqués OU déjà expirés. Sans cette purge périodique,
     * la table RefreshToken grossit indéfiniment (un jeton est créé à chaque
     * connexion et n'est jamais effacé).
     *
     * @return nombre de lignes supprimées
     */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.revoked = true OR t.expiryDate < :now")
    int deleteStale(@Param("now") LocalDateTime now);
}
