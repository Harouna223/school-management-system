package com.school.service;

import com.school.entity.User;
import com.school.repository.RefreshTokenRepository;
import com.school.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Comptabilisation des échecs de connexion.
 *
 * <p>Raison d'être de cette classe : {@code AuthService.login} est
 * transactionnel et relance l'exception d'authentification pour que le client
 * reçoive un 401. Écrire le compteur d'échecs dans cette même transaction
 * revenait donc à l'annuler (rollback) : le compteur restait à zéro, le
 * verrouillage après {@value #MAX_ATTEMPTS} échecs ne se déclenchait jamais et
 * le mot de passe pouvait être attaqué par force brute sans limite.</p>
 *
 * <p>{@link Propagation#REQUIRES_NEW} garantit que l'incrément est validé dans
 * sa propre transaction, indépendamment du rollback de la connexion.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {

    /** Nombre maximal de tentatives avant verrouillage du compte. */
    public static final int MAX_ATTEMPTS = 5;
    /** Durée du verrouillage en minutes. */
    public static final long LOCK_MINUTES = 15;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Enregistre un échec de connexion et verrouille le compte au-delà du seuil.
     *
     * @param userId identifiant de l'utilisateur qui a échoué
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerFailedAttempt(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setFailedAttempts(user.getFailedAttempts() + 1);
            if (user.getFailedAttempts() >= MAX_ATTEMPTS) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                log.warn("Compte verrouillé {} min après {} échecs : {}",
                        LOCK_MINUTES, MAX_ATTEMPTS, user.getUsername());
            }
            userRepository.save(user);
        });
    }

    /** Purge un verrouillage et remet le compteur à zéro (réinitialisation admin). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetAttempts(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        });
    }

    /**
     * Révoque TOUS les jetons de rafraîchissement d'un utilisateur.
     *
     * <p>Appelée depuis {@code AuthService.refresh}, qui est transactionnel et
     * relance ensuite une {@code TokenRefreshException} : sans transaction
     * indépendante, l'exception annulerait aussi la révocation, et le jeton
     * délivré à un attaquant (ou à un compte désactivé) resterait valide.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllRefreshTokens(Long userId) {
        if (userId != null) {
            refreshTokenRepository.deleteByUserId(userId);
        }
    }

    /** Vrai si le compte est actuellement verrouillé. */
    public boolean isLocked(User user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now());
    }
}
