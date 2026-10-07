package com.school.integration;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Base64;

/**
 * Socle des tests d'integration : application Spring Boot complete sur une base
 * MySQL de test (Testcontainers si Docker est joignable, sinon base dediee).
 *
 * <p>Complete les tests unitaires (mockes) par la validation de ce qui ne peut
 * l'etre que dans un vrai contexte : SQL genere par Spring Data, transactions,
 * serialisation JSON et regles de securite HTTP.</p>
 *
 * <p>Configuration : {@code ddl-auto=validate} (schema cree par Flyway puis
 * verifie) et {@code open-in-view=false}, c'est-a-dire la configuration
 * IDENTIQUE a la production depuis la fin de la phase 7.</p>
 *
 * <p>Si aucune base de test n'est joignable (Docker inaccessible ET pas de base
 * de test locale), les tests sont <strong>desactives</strong> et non en echec :
 * la suite reste verte et le diagnostic est explicite dans les logs.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);

    /** Secret HS256 de test (>= 32 octets decodes), inutilise hors tests. */
    protected static final String TEST_JWT_SECRET = Base64.getEncoder()
            .encodeToString("phase6-secret-de-test-uniquement-0123456789".getBytes(StandardCharsets.UTF_8));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> MySqlTestContainer.JDBC_URL);
        registry.add("spring.datasource.username", () -> MySqlTestContainer.USERNAME);
        registry.add("spring.datasource.password", () -> MySqlTestContainer.PASSWORD);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("app.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("spring.jpa.open-in-view", () -> "false");
    }

    /**
     * Verifie l'accessibilite de la base AVANT le chargement du contexte Spring.
     * En cas d'echec, la classe de tests est ignoree proprement : mieux vaut
     * aucun test d'integration qu'un echec qui masque les autres problemes.
     */
    @BeforeAll
    static void verifierBaseDeTest() {
        try (Connection ignored = DriverManager.getConnection(MySqlTestContainer.JDBC_URL,
                MySqlTestContainer.USERNAME, MySqlTestContainer.PASSWORD)) {
            log.info("Base de test prete : {}", MySqlTestContainer.JDBC_URL.replaceAll("[?].*", ""));
        } catch (Exception ex) {
            log.warn("Base de test injoignable ({}) - tests d'integration DESACTIVES sur cette machine. "
                    + "Verifier Docker, ou definir TEST_DB_URL / TEST_DB_USER / TEST_DB_PASSWORD.",
                    ex.getMessage());
            Assumptions.abort("Base de test d'integration injoignable : " + ex.getMessage());
        }
    }

    @Autowired
    protected WebApplicationContext webApplicationContext;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        // Vide le contexte de sécurité peuplé par la requête précédente :
        // MockMvc réutilise le thread du test et ne doit hériter d'aucune
        // authentification entre deux requêtes, sinon un test « sans jeton »
        // verrait à tort une session et renverrait 200 au lieu de 401.
        SecurityContextHolder.clearContext();
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }
}