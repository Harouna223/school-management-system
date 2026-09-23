package com.school.config;

import io.jsonwebtoken.io.Decoders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Alertes de sécurité au démarrage : secret JWT par défaut ou trop court pour HS256.
 */
@Component
@Slf4j
public class SecurityStartupWarning implements CommandLineRunner {

    private static final String DEFAULT_JWT_SECRET =
            "Y2hhbmdlLXRoaXMtc2VjcmV0LWtleS1pbi1wcm9kdWN0aW9uLXdpdGgtYTM3LWNyYXdhbGxlbg==";

    /** HS256 exige une clé d'au moins 256 bits (32 octets) une fois décodée. */
    private static final int MIN_SECRET_BYTES = 32;

    private final String jwtSecret;

    public SecurityStartupWarning(@Value("${app.jwt.secret}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    @Override
    public void run(String... args) {
        if (DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            log.warn("SECURITE : le secret JWT par défaut est utilisé. "
                    + "Définissez la variable d'environnement JWT_SECRET avant toute mise en production.");
            return;
        }
        try {
            byte[] key = Decoders.BASE64.decode(jwtSecret);
            if (key.length < MIN_SECRET_BYTES) {
                log.warn("SECURITE : le secret JWT décodé fait {} octets (< {} requis pour HS256). "
                        + "Utilisez un secret plus long pour garantir la sécurité de la signature.",
                        key.length, MIN_SECRET_BYTES);
            }
        } catch (IllegalArgumentException ex) {
            log.warn("SECURITE : le secret JWT n'est pas une chaîne Base64 valide : {}", ex.getMessage());
        }
    }
}
