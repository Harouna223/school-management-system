package com.school.service;

import com.school.entity.User;
import com.school.exception.TooManyLoginAttemptsException;
import com.school.repository.RefreshTokenRepository;
import com.school.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /** Échecs tolérés depuis une même IP sur la fenêtre glissante. */
    public static final int MAX_FAILURES_PER_IP = 30;
    /** Fenêtre d'observation des échecs par IP. */
    static final Duration IP_WINDOW = Duration.ofMinutes(15);
    /** Blocages progressifs : 1re série, 2e, puis 3e et suivantes. */
    private static final Duration[] IP_BLOCK_DURATIONS = {
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15)};
    /** Série d'échecs oubliée après une heure sans nouvelle tentative. */
    private static final Duration STRIKE_RESET = Duration.ofHours(1);
    /** Au-delà, purge des entrées inactives pour borner la mémoire. */
    private static final int IP_CACHE_LIMIT = 10_000;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    /** Seuil par IP, ajustable par configuration (abaissé dans les tests ciblés). */
    @Value("${app.security.login.max-failures-per-ip:30}")
    private int maxFailuresPerIp = MAX_FAILURES_PER_IP;

    /** État par adresse IP (mono-instance : suffisant pour cette défense en profondeur). */
    private final Map<String, IpFailures> ipFailures = new ConcurrentHashMap<>();

    /**
     * Refuse la tentative si l'adresse IP a dépassé son quota d'échecs et se
     * trouve encore dans sa période de blocage.
     *
     * @throws TooManyLoginAttemptsException avec le délai restant en secondes
     */
    public void assertIpAllowed(String ip) {
        IpFailures failures = ipFailures.get(ipKey(ip));
        if (failures == null) {
            return;
        }
        synchronized (failures) {
            Instant now = Instant.now();
            if (failures.blockedUntil != null && failures.blockedUntil.isAfter(now)) {
                long retryAfter = Math.max(1, Duration.between(now, failures.blockedUntil).toSeconds());
                throw new TooManyLoginAttemptsException(retryAfter);
            }
            if (failures.blockedUntil != null) {
                failures.blockedUntil = null; // blocage terminé
            }
        }
    }

    /**
     * Comptabilise un échec pour l'adresse IP et déclenche un blocage progressif
     * (1 min, 5 min, puis 15 min) au-delà du seuil de la fenêtre glissante.
     */
    public void registerIpFailure(String ip) {
        String key = ipKey(ip);
        IpFailures failures = ipFailures.computeIfAbsent(key, k -> new IpFailures());
        synchronized (failures) {
            Instant now = Instant.now();
            if (failures.lastFailure != null
                    && Duration.between(failures.lastFailure, now).compareTo(STRIKE_RESET) > 0) {
                failures.strikes = 0;
                failures.timestamps.clear();
            }
            failures.timestamps.addLast(now);
            while (!failures.timestamps.isEmpty()
                    && Duration.between(failures.timestamps.peekFirst(), now).compareTo(IP_WINDOW) > 0) {
                failures.timestamps.removeFirst();
            }
            failures.lastFailure = now;
            if (failures.timestamps.size() >= maxFailuresPerIp) {
                Duration block = IP_BLOCK_DURATIONS[
                        Math.min(failures.strikes, IP_BLOCK_DURATIONS.length - 1)];
                failures.strikes++;
                failures.blockedUntil = now.plus(block);
                failures.timestamps.clear();
                log.warn("Limitation par IP : {} bloquée {} min après {} échecs",
                        key, block.toMinutes(), maxFailuresPerIp);
            }
        }
        if (ipFailures.size() > IP_CACHE_LIMIT) {
            purgeInactiveIps();
        }
    }

    /** Connexion réussie : la série d'échecs de cette IP est remise à zéro. */
    public void resetIp(String ip) {
        ipFailures.remove(ipKey(ip));
    }

    private String ipKey(String ip) {
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }

    private void purgeInactiveIps() {
        Instant threshold = Instant.now().minus(STRIKE_RESET);
        Iterator<Map.Entry<String, IpFailures>> it = ipFailures.entrySet().iterator();
        while (it.hasNext()) {
            IpFailures failures = it.next().getValue();
            synchronized (failures) {
                if (failures.lastFailure == null || failures.lastFailure.isBefore(threshold)) {
                    it.remove();
                }
            }
        }
    }

    private static final class IpFailures {
        private final Deque<Instant> timestamps = new ArrayDeque<>();
        private Instant lastFailure;
        private Instant blockedUntil;
        private int strikes;
    }

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
