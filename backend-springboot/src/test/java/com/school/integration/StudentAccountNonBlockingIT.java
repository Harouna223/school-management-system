package com.school.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Non-régression : la création d'un élève ne doit <strong>jamais</strong> échouer
 * à cause du compte de connexion qui lui est rattaché.
 *
 * <p><b>Bug constaté en réel :</b> « Donnée en conflit » (409) à l'enregistrement
 * d'un nouvel élève. Cause : {@code users.email} est UNIQUE, et
 * {@code AuthService.createLinkedAccount} ne vérifiait pas cette contrainte —
 * contrairement à {@code register()}. Dès qu'un email était déjà rattaché à un
 * compte (frère/sœur, parent saisi avec le même email, réinscription…), l'insertion
 * violait la contrainte et le dossier entier était perdu.</p>
 *
 * <p><b>Second bug, plus grave :</b> le nom d'utilisateur n'était pas non plus
 * désambiguïsé. Un homonyme réutilisait le compte existant, ce qui rattachait
 * silencieusement l'élève au compte d'une autre famille — et inversement donnait
 * à cette famille accès à ses notes.</p>
 *
 * <p>Ces tests exercent le vrai contrôleur, le vrai encodeur de mot de passe et la
 * vraie base : le correctif ne peut plus régresser silencieusement.</p>
 */
class StudentAccountNonBlockingIT extends AbstractIntegrationTest {

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "Admin@123";

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Un email déjà rattaché à un compte ne bloque plus la création de l'élève")
    void emailDejaUtiliseNeBloquePlusLaCreation() throws Exception {
        String token = loginAdmin();
        String emailPartage = "it.partage." + suffixe() + "@school.local";

        JsonNode premier = creerEleve(token, "Aicha", "Kamdem", null, emailPartage);
        // Avant le correctif, cette seconde requête renvoyait 409 : l'élève
        // n'était jamais créé et le secrétariat devait changer l'email à la main.
        JsonNode second = creerEleve(token, "Bello", "Nana", null, emailPartage);

        assertThat(second.path("id").asLong()).isNotEqualTo(premier.path("id").asLong());

        // L'email reste attaché au DOSSIER : c'est l'information métier, elle ne
        // doit pas disparaître parce que le compte de connexion n'en veut pas.
        assertThat(second.path("email").asText()).isEqualTo(emailPartage);

        // Chaque élève possède bien SON compte, distinct de celui du premier.
        assertThat(second.path("hasAccount").asBoolean()).isTrue();
        assertThat(second.path("accountUsername").asText())
                .isNotBlank()
                .isNotEqualTo(premier.path("accountUsername").asText());
    }

    @Test
    @DisplayName("Deux élèves homonymes ne partagent plus le même compte de connexion")
    void homonymesNePartagentPasLeCompte() throws Exception {
        String token = loginAdmin();
        String racine = suffixe();
        String username = "it.homonyme." + racine;

        JsonNode premier = creerEleve(token, "Aicha", "Kamdem", username,
                "it.h1." + racine + "@school.local");
        JsonNode second = creerEleve(token, "Aicha", "Kamdem", username,
                "it.h2." + racine + "@school.local");

        // Le premier obtient exactement le nom demandé...
        assertThat(premier.path("accountUsername").asText()).isEqualTo(username);
        // ...le second reçoit un suffixe d'unicité au lieu d'être rattaché au
        // compte du premier (ce qui lui aurait donné accès à ses notes).
        assertThat(second.path("accountUsername").asText())
                .isNotEqualTo(username)
                .startsWith(username);
        assertThat(second.path("id").asLong()).isNotEqualTo(premier.path("id").asLong());
    }

    @Test
    @DisplayName("Email ET nom d'utilisateur identiques : la création aboutit quand même")
    void doublonCompletAboutit() throws Exception {
        String token = loginAdmin();
        String racine = suffixe();
        String username = "it.doublon." + racine;
        String email = "it.doublon." + racine + "@school.local";

        JsonNode premier = creerEleve(token, "Aicha", "Kamdem", username, email);
        JsonNode second = creerEleve(token, "Bello", "Nana", username, email);

        assertThat(second.path("id").asLong()).isNotEqualTo(premier.path("id").asLong());
        assertThat(second.path("accountUsername").asText())
                .isNotEqualTo(premier.path("accountUsername").asText());
        assertThat(second.path("email").asText()).isEqualTo(email);
    }

    // ------------------------------------------------------------------ outils

    private String suffixe() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Crée un élève par l'API. Aucun mot de passe n'est fourni : le serveur en
     * génère un, ce qui rend {@code generatedPassword} et surtout
     * {@code accountUsername} observables dans la réponse (le nom d'utilisateur
     * effectif n'est renvoyé que dans ce cas).
     */
    private JsonNode creerEleve(String token, String prenom, String nom,
                                String username, String email) throws Exception {
        StringBuilder corps = new StringBuilder("{")
                .append("\"firstName\":\"").append(prenom).append("\",")
                .append("\"lastName\":\"").append(nom).append("\",")
                .append("\"gender\":\"FEMALE\",")
                .append("\"email\":\"").append(email).append("\"");
        if (username != null) {
            corps.append(",\"username\":\"").append(username).append("\"");
        }
        corps.append("}");

        String reponse = mockMvc.perform(post("/api/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.generatedPassword", notNullValue()))
                .andExpect(jsonPath("$.data.hasAccount").value(true))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(reponse).path("data");
    }

    private String loginAdmin() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN + "\",\"password\":\""
                                + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }
}
