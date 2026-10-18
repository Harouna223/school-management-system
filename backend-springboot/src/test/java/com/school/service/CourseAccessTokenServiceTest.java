package com.school.service;

import com.school.config.AppProperties;
import com.school.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests des jetons de lecture des fichiers pédagogiques.
 *
 * <p>Le jeton remplace l'en-tête {@code Authorization} pour les balises
 * {@code <video>} / {@code <iframe>}. Il doit donc être impossible à falsifier,
 * expirer, et ne jamais servir un autre usage que celui prévu.</p>
 */
class CourseAccessTokenServiceTest {

    private AppProperties properties;
    private CourseAccessTokenService service;

    @BeforeEach
    void setUp() {
        properties = new AppProperties();
        properties.getJwt().setSecret("secret-de-test-suffisamment-long-pour-hmac-sha256");
        properties.getCourses().setStreamTokenTtlMinutes(30);
        service = new CourseAccessTokenService(properties);
    }

    @Test
    @DisplayName("Un jeton émis est vérifiable et porte la ressource, le compte et l'usage")
    void jetonValide_estVerifie() {
        String token = service.issue(12L, 34L, CourseAccessTokenService.PURPOSE_STREAM, 600);

        CourseAccessTokenService.Payload payload =
                service.verify(token, CourseAccessTokenService.PURPOSE_STREAM);

        assertThat(payload.resourceId()).isEqualTo(12L);
        assertThat(payload.userId()).isEqualTo(34L);
        assertThat(payload.purpose()).isEqualTo(CourseAccessTokenService.PURPOSE_STREAM);
    }

    @Test
    @DisplayName("Un jeton ne peut pas servir un autre usage que celui pour lequel il a été émis")
    void jeton_dunAutreUsage_estRefuse() {
        String token = service.issue(12L, 34L, CourseAccessTokenService.PURPOSE_PREVIEW, 600);

        assertThatThrownBy(() -> service.verify(token, CourseAccessTokenService.PURPOSE_DOWNLOAD))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("usage");
    }

    @Test
    @DisplayName("Un jeton expiré est refusé")
    void jetonExpire_estRefuse() throws InterruptedException {
        String token = service.issue(12L, 34L, CourseAccessTokenService.PURPOSE_STREAM, 1);
        // L'expiration est comparée en SECONDES entières : selon l'instant exact
        // de l'émission, il faut franchir deux frontières de seconde pour être
        // sûr que `now > expiresAt`. Un sommeil de 1,5 s laissait passer le cas
        // où l'émission a lieu à x,2 s (expiration à x+1, encore valide à x+1,7).
        Thread.sleep(2200);

        assertThatThrownBy(() -> service.verify(token, CourseAccessTokenService.PURPOSE_STREAM))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expiré");
    }

    @Test
    @DisplayName("Modifier la charge utile invalide la signature")
    void chargeUtileFalsifiee_estRefusee() {
        String token = service.issue(12L, 34L, CourseAccessTokenService.PURPOSE_STREAM, 600);
        int dot = token.lastIndexOf('.');
        // Remplace la ressource 12 par 99 sans retoucher la signature.
        String forged = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("99|34|stream|9999999999".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + token.substring(dot);

        assertThatThrownBy(() -> service.verify(forged, CourseAccessTokenService.PURPOSE_STREAM))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalide");
    }

    @Test
    @DisplayName("Un jeton malformé est refusé proprement")
    void jetonMalforme_estRefuse() {
        assertThatThrownBy(() -> service.verify("n-importe-quoi", CourseAccessTokenService.PURPOSE_STREAM))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Un usage inconnu est refusé à l'émission")
    void usageInconnu_estRefuse() {
        assertThatThrownBy(() -> service.issue(1L, 2L, "admin", 60))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Usage");
    }

    @Test
    @DisplayName("La durée de vie configurée est exprimée en secondes")
    void ttl_estEnSecondes() {
        assertThat(service.ttlSeconds()).isEqualTo(30 * 60L);
    }
}
