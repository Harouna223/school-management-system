package com.school.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de securite HTTP de bout en bout (PHASE 6).
 *
 * <p>Ce sont exactement les tests qui manquaient au deploiement : ils exercent
 * les vrais filtres Spring Security, le vrai JSON et la vraie base.
 * Le test /uploads aurait attrape le bug de securite D9 avant mise en
 * production.</p>
 */
class AuthAndUploadsIT extends AbstractIntegrationTest {

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "Admin@123";

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("la connexion admin reussit et renvoie un jeton JWT")
    void loginReussit() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.data.user.username").value(ADMIN));
    }

    @Test
    @DisplayName("un mot de passe errone est refuse (401 + verrouillage progressif")
    void loginMauvaisMotDePasseRefuse() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN + "\",\"password\":\"faux-mot-de-passe\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("D9 : /uploads sans jeton est refuse (401)")
    void uploadsSansJetonRefuse() throws Exception {
        mockMvc.perform(get("/uploads/students/inconnu.png"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("D9 : /uploads avec jeton et fichier inconnu renvoie 404 (anti-traversal)")
    void uploadsAvecJetonFichierInconnu() throws Exception {
        String token = login();
        mockMvc.perform(get("/uploads/students/inconnu.png")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("D9 : une traversee de repertoire ne peut jamais servir un fichier")
    void uploadsTraversalRefuse() throws Exception {
        String token = login();
        // Le conteneur normalise le chemin AVANT le routage : la requête ne doit
        // jamais aboutir a un fichier (4xx obligatoire, 2xx interdit).
        mockMvc.perform(get("/uploads/../secret")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("un endpoint protege sans jeton renvoie 401")
    void apiProtegeSansJeton() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("avec le jeton admin, le endpoint protege renvoie les statistiques")
    void apiProtegeAvecJeton() throws Exception {
        String token = login();
        mockMvc.perform(get("/api/dashboard/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /** connecte admin et retourne le jeton JWT. */
    private String login() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.path("data").path("accessToken").asText();
    }
}