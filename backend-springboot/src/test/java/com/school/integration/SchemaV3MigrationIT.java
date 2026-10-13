package com.school.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration de la migration V3 (LOT 2).
 *
 * <p>Deux divergences entre le schema Flyway et le code metier ne pouvaient etre
 * constatees que sur une vraie base :</p>
 * <ul>
 *   <li>{@code students.class_id} etait {@code NOT NULL} alors que l'entite JPA,
 *       le service et l'interface autorisent l'absence de classe (inscription
 *       d'un eleve non encore affecte) ;</li>
 *   <li>{@code fk_grades_exam} et {@code fk_exams_class} etaient en
 *       {@code ON DELETE CASCADE} : supprimer une classe ou une evaluation
 *       detruisait les notes en silence.</li>
 * </ul>
 */
class SchemaV3MigrationIT extends AbstractIntegrationTest {

    private Connection connexion() throws Exception {
        return DriverManager.getConnection(MySqlTestContainer.JDBC_URL,
                MySqlTestContainer.USERNAME, MySqlTestContainer.PASSWORD);
    }

    @Test
    @DisplayName("Flyway a bien applique V3 (migration enregistree dans l'historique)")
    void migrationV3Enregistree() throws Exception {
        try (Connection conn = connexion();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '3' AND success = 1");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            assertThat(rs.getInt(1)).as("V3 presente et reussie").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("students.class_id est devenu NULLABLE (inscription sans classe possible)")
    void classIdNullable() throws Exception {
        try (Connection conn = connexion();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT IS_NULLABLE FROM information_schema.COLUMNS "
                             + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'students' "
                             + "AND COLUMN_NAME = 'class_id'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            assertThat(rs.getString(1)).isEqualTo("YES");
        }
    }

    @Test
    @DisplayName("les notes ne sont plus supprimables en cascade avec leur evaluation")
    void cascadeRemplaceeParRestrict() throws Exception {
        assertThat(regleDeSuppression("grades", "fk_grades_exam")).isEqualTo("RESTRICT");
        assertThat(regleDeSuppression("exams", "fk_exams_class")).isEqualTo("RESTRICT");
    }

    @Test
    @DisplayName("inscrire un élève SANS classe est accepté (plus de 409 trompeur)")
    void inscriptionSansClasseAcceptee() throws Exception {
        String token = jetonAdmin();

        // Avant V3, cette requête renvoyait 409 « Donnée en conflit » (la colonne
        // class_id était NOT NULL alors que l'entité et l'interface autorisent
        // l'absence de classe pour un élève non encore affecté).
        mockMvc.perform(post("/api/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Sans\",\"lastName\":\"Classe\","
                                + "\"gender\":\"MALE\",\"enrollmentDate\":\"2026-09-01\","
                                + "\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists());
    }

    /** Ouvre une session admin et renvoie le jeton. */
    private String jetonAdmin() throws Exception {
        String corps = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(corps).path("data").path("accessToken").asText();
    }

    private String regleDeSuppression(String table, String contrainte) throws Exception {        try (Connection conn = connexion();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS "
                             + "WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = ? "
                             + "AND CONSTRAINT_NAME = ?")) {
            ps.setString(1, table);
            ps.setString(2, contrainte);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("contrainte %s presente sur %s", contrainte, table).isTrue();
                return rs.getString(1);
            }
        }
    }
}
