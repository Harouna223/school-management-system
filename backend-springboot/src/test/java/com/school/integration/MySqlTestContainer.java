package com.school.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Base MySQL des tests d'integration (PHASE 6), resolue automatiquement :
 *
 * <ol>
 *   <li><b>Testcontainers</b> (preferé) : conteneur MySQL 8.4 jetable, demarre
 *       une seule fois pour toute la JVM. C'est le mode utilise en CI.</li>
 *   <li><b>Repli</b> : si Docker n'est pas joignable depuis la JVM (cas connu
 *       sous Docker Desktop / WSL2 sur Windows), une base MySQL de test dediee
 *       est utilisee (variable {@code TEST_DB_URL} ou defaut local).</li>
 * </ol>
 *
 * <p><b>SECURITE :</b> le repli est refuse si le nom de la base ne contient pas
 * le marqueur « test ». Les donnees reelles (base « school_management ») ne
 * peuvent donc JAMAIS etre modifiees par un test.</p>
 *
 * <p>Dans tous les cas, le schema de test est <strong>recree</strong> avant le
 * chargement du contexte Spring (drop + create via JDBC) : chaque exécution
 * part d'une base vierge, sans dependre du mode de ddl d'Hibernate.</p>
 */
public final class MySqlTestContainer {

    private static final Logger log = LoggerFactory.getLogger(MySqlTestContainer.class);

    private static final String SAFETY_MARKER = "test";

    public static final String JDBC_URL;
    public static final String USERNAME;
    public static final String PASSWORD;

    static {
        String url;
        String user;
        String password;
        try {
            MySQLContainer<?> tc = new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("school_test")
                    .withUsername("sms_test")
                    .withPassword("sms_test");
            tc.start();
            url = tc.getJdbcUrl();
            user = tc.getUsername();
            password = tc.getPassword();
            log.info("Base de test : conteneur MySQL jetable (Testcontainers)");
        } catch (Throwable ex) {
            log.warn("Docker non joignable depuis la JVM ({}) - repli sur une base de test dediee.",
                    ex.getMessage());
            url = externalTestDbUrl();
            user = property("TEST_DB_USER", "sms_test");
            password = property("TEST_DB_PASSWORD", "sms_test");
        }
        recreateSchema(url, user, password);
        JDBC_URL = url;
        USERNAME = user;
        PASSWORD = password;
    }

    private static void recreateSchema(String url, String user, String password) {
        String dbName = databaseNameOf(url);
        assertIsSafeTestDatabase(dbName);
        // Connexion au serveur (sans schema cible) pour recrer la base de test.
        String rootUrl = url.substring(0, url.lastIndexOf('/') + 1);
        try (Connection conn = DriverManager.getConnection(rootUrl, user, password);
             Statement st = conn.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS `" + dbName + "`");
            st.execute("CREATE DATABASE `" + dbName
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            log.info("Schema de test recree : {}", dbName);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Impossible de recrer la base de test «" + dbName + "» : " + ex.getMessage(), ex);
        }
    }

    /** URL de la base de test de repli (port libre par defaut). */
    private static String externalTestDbUrl() {
        String url = property("TEST_DB_URL",
                "jdbc:mysql://localhost:3308/school_test"
                        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        assertIsSafeTestDatabase(databaseNameOf(url));
        return url;
    }

    private static String databaseNameOf(String url) {
        String withoutParams = url.substring(0, url.indexOf('?') >= 0 ? url.indexOf('?') : url.length());
        return withoutParams.substring(withoutParams.lastIndexOf('/') + 1);
    }

    /** Refuse toute base dont le nom ne contient pas « test ». */
    private static void assertIsSafeTestDatabase(String dbName) {
        if (!dbName.toLowerCase().contains(SAFETY_MARKER)) {
            throw new IllegalStateException(
                    "Refus de lancer les tests : la base «" + dbName + "» ne contient pas le "
                            + "marqueur «" + SAFETY_MARKER + "». Les tests ne doivent jamais "
                            + "ecrire dans les donnees de production.");
        }
    }

    private static String property(String key, String fallback) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            value = System.getenv(key);
        }
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private MySqlTestContainer() {
    }
}