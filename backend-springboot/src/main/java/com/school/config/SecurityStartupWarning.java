package com.school.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Alerte de sécurité au démarrage si le secret JWT par défaut est encore utilisé.
 */
@Component
@Slf4j
public class SecurityStartupWarning implements CommandLineRunner {

    private static final String DEFAULT_JWT_SECRET =
            "Y2hhbmdlLXRoaXMtc2VjcmV0LWtleS1pbi1wcm9kdWN0aW9uLXdpdGgtYTM3LWNyYXdhbGxlbg==";

    private final String jwtSecret;

    public SecurityStartupWarning(@Value("${app.jwt.secret}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    @Override
    public void run(String... args) {
        if (DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            log.warn("SECURITE : le secret JWT par défaut est utilisé. "
                    + "Définissez la variable d'environnement JWT_SECRET avant toute mise en production.");
        }
    }
}
