package com.school.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.entity.User;
import com.school.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration des verrous d'authentification (LOT 1).
 *
 * <p>Ces deux regressions ne pouvaient PAS etre vues par les tests unitaires :
 * le compteur d'echecs etait verifie sur l'objet en memoire d'un mock, alors
 * qu'en base il etait annule par le rollback de {@code login()} ; et la
 * revocation des jetons, ecrite dans la transaction de {@code refresh()}, etait
 * annulee par l'exception qui suit. Il faut donc un vrai MySQL et de vraies
 * transactions HTTP pour les constater.</p>
 */
class AuthLockoutIT extends AbstractIntegrationTest {

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "Admin@123";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void debloquerAdmin() throws Exception {
        // Les tests ci-dessous verrouillent volontairement le compte admin :
        // on restaure son etat pour ne pas polluer les autres tests.
        try (Connection conn = DriverManager.getConnection(
                MySqlTestContainer.JDBC_URL, MySqlTestContainer.USERNAME, MySqlTestContainer.PASSWORD);
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE users SET failed_attempts = 0, locked_until = NULL, enabled = TRUE "
                             + "WHERE username = ?")) {
            ps.setString(1, ADMIN);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("les echecs de connexion sont PERSISTES (le verrouillage ne peut plus etre annule)")
    void echecsPersistesEtVerrouillageEffectif() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + ADMIN + "\",\"password\":\"faux-" + i + "\"}"))
                    .andExpect(status().isUnauthorized());
        }

        // Lecture DIRECTE en base : c'est le point du bug. Avant correction,
        // failed_attempts valait 0 et locked_until restait NULL.
        try (Connection conn = DriverManager.getConnection(
                MySqlTestContainer.JDBC_URL, MySqlTestContainer.USERNAME, MySqlTestContainer.PASSWORD);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT failed_attempts, locked_until FROM users WHERE username = ?")) {
            ps.setString(1, ADMIN);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("failed_attempts"))
                        .as("compteur d'echecs persiste")
                        .isGreaterThanOrEqualTo(5);
                assertThat(rs.getTimestamp("locked_until"))
                        .as("verrouillage effectif apres 5 echecs")
                        .isNotNull();
            }
        }

        // Un mot de passe CORRECT ne doit plus passer : le verrou est actif.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("un compte desactive ne peut plus rafraichir son jeton et sa famille de jetons est revoquee")
    void compteDesactiveNePeutPlusRafraichir() throws Exception {
        String corps = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(corps).path("data").path("refreshToken").asText();
        assertThat(refreshToken).isNotBlank();

        User admin = userRepository.findByUsername(ADMIN).orElseThrow();
        admin.setEnabled(false);
        userRepository.saveAndFlush(admin);

        // Le refresh etait permitAll et ne testait jamais enabled : le compte
        // desactive continuait d'obtenir des jetons pendant 7 jours.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());

        Integer restants = compterJetons(admin.getId());
        assertThat(restants)
                .as("jetons de rafraichissement revoques (transaction independante)")
                .isZero();

        admin.setEnabled(true);
        userRepository.saveAndFlush(admin);
    }

    private Integer compterJetons(Long userId) throws Exception {
        try (Connection conn = DriverManager.getConnection(
                MySqlTestContainer.JDBC_URL, MySqlTestContainer.USERNAME, MySqlTestContainer.PASSWORD);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
