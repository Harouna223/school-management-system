package com.school.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests du durcissement du secret JWT (D15) : en mode
 * {@code REQUIRE_SECURE_SECRETS=true} (production), un secret par défaut ou
 * trop court doit faire échouer le démarrage ; sinon, simple alerte.
 */
class SecurityStartupWarningTest {

    private static final String DEFAULT_SECRET =
            "Y2hhbmdlLXRoaXMtc2VjcmV0LWtleS1pbi1wcm9kdWN0aW9uLXdpdGgtYTM3LWNyYXdhbGxlbg==";

    @Test
    @DisplayName("D15 : secret par défaut + REQUIRE_SECURE_SECRETS=true → démarrage refusé")
    void defaultSecretFailsStartupWhenSecureRequired() {
        SecurityStartupWarning warning = new SecurityStartupWarning(DEFAULT_SECRET, true);
        assertThatThrownBy(warning::run).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret JWT par défaut");
    }

    @Test
    @DisplayName("D15 : secret par défaut sans exigence stricte → simple alerte (pas d'échec)")
    void defaultSecretOnlyWarnsWhenSecureNotRequired() {
        SecurityStartupWarning warning = new SecurityStartupWarning(DEFAULT_SECRET, false);
        assertThatCode(warning::run).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("D15 : secret trop court + exigence stricte → démarrage refusé")
    void shortSecretFailsStartupWhenSecureRequired() {
        String shortSecret = Base64.getEncoder().encodeToString(new byte[8]); // 8 octets < 32
        SecurityStartupWarning warning = new SecurityStartupWarning(shortSecret, true);
        assertThatThrownBy(warning::run).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("octets");
    }

    @Test
    @DisplayName("secret fort (≥ 32 octets) → aucun échec, même en mode strict")
    void strongSecretPasses() {
        String strong = Base64.getEncoder().encodeToString(new byte[48]);
        SecurityStartupWarning warning = new SecurityStartupWarning(strong, true);
        assertThatCode(warning::run).doesNotThrowAnyException();
    }
}