package com.school.service;

import com.school.entity.User;
import com.school.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests du compteur d'échecs de connexion.
 *
 * <p>Pourquoi ce fichier existe : le verrouillage de compte après 5 échecs était
 * testé uniquement sur l'objet en mémoire d'un mock — il passait donc au vert
 * alors qu'en production l'écriture était annulée par le rollback de
 * {@code AuthService.login}. Ces tests-ci portent sur le service qui écrit
 * réellement le compteur, dans sa propre transaction.</p>
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private com.school.repository.RefreshTokenRepository refreshTokenRepository;

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService(userRepository, refreshTokenRepository);
    }

    private User utilisateur(Long id, int failedAttempts, LocalDateTime lockedUntil) {
        return User.builder()
                .id(id)
                .username("user" + id)
                .failedAttempts(failedAttempts)
                .lockedUntil(lockedUntil)
                .build();
    }

    @Test
    @DisplayName("un échec incrémente le compteur SANS verrouiller tant que le seuil n'est pas atteint")
    void unEchecIncrementeSansVerrouiller() {
        User user = utilisateur(1L, 2, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.registerFailedAttempt(1L);

        assertThat(user.getFailedAttempts()).isEqualTo(3);
        assertThat(user.getLockedUntil()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("le 5e échec verrouille le compte 15 minutes")
    void cinquiemeEchecVerrouille() {
        User user = utilisateur(1L, 4, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.registerFailedAttempt(1L);

        assertThat(user.getFailedAttempts()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isNotNull();
        assertThat(user.getLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(14));
        assertThat(user.getLockedUntil()).isBefore(LocalDateTime.now().plusMinutes(16));
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("un échec sur un identifiant inconnu n'écrit rien")
    void utilisateurInconnuNAucuneEcriture() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        service.registerFailedAttempt(99L);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("la réinitialisation efface le verrou et le compteur")
    void resetEffaceLeVerrou() {
        User user = utilisateur(1L, 5, LocalDateTime.now().plusMinutes(10));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.resetAttempts(1L);

        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("isLocked ne considère verrouillé que si l'échéance est future")
    void isLockedRespecteLEcheance() {
        assertThat(service.isLocked(utilisateur(1L, 0, null))).isFalse();
        assertThat(service.isLocked(utilisateur(2L, 5, LocalDateTime.now().minusMinutes(1)))).isFalse();
        assertThat(service.isLocked(utilisateur(3L, 5, LocalDateTime.now().plusMinutes(1)))).isTrue();
    }

    @Test
    @DisplayName("la révocation coupe tous les jetons de l'utilisateur")
    void revocationCoupeTousLesJetons() {
        service.revokeAllRefreshTokens(7L);

        verify(refreshTokenRepository).deleteByUserId(7L);
    }

    @Test
    @DisplayName("la révocation sans identifiant ne touche à rien")
    void revocationSansIdentifiantEstSansEffet() {
        service.revokeAllRefreshTokens(null);

        verify(refreshTokenRepository, never()).deleteByUserId(any());
    }

    @Test
    @DisplayName("l'incrément et la révocation sont marqués REQUIRES_NEW (survit au rollback de login/refresh)")
    void propagationRequiresNewObligatoire() throws Exception {
        // Garde-fou anti-régression : si quelqu'un retire REQUIRES_NEW, le
        // compteur d'échecs (annulé par le rollback de login) et la révocation
        // (annulée par l'exception de refresh) redeviendraient inopérants.
        for (String methode : new String[]{"registerFailedAttempt", "revokeAllRefreshTokens"}) {
            var m = LoginAttemptService.class.getMethod(methode, Long.class);
            var tx = m.getAnnotation(org.springframework.transaction.annotation.Transactional.class);
            assertThat(tx).as("annotation @Transactional sur " + methode).isNotNull();
            assertThat(tx.propagation())
                    .as("propagation de " + methode)
                    .isEqualTo(org.springframework.transaction.annotation.Propagation.REQUIRES_NEW);
        }
    }
}
