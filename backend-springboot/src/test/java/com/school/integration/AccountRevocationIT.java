package com.school.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Révocation des comptes rattachés à un dossier (élève, enseignant).
 *
 * <p>Garde-fou d'un bug constaté en réel : supprimer un élève laissait son
 * compte de connexion parfaitement valide — la séquence « créer → se connecter
 * → supprimer → se reconnecter » renvoyait 200 après la suppression du dossier.
 * Ces tests exercent le vrai filtre de sécurité, le vrai encodeur de mot de
 * passe et la vraie base : une régression du service ne peut plus passer.</p>
 */
class AccountRevocationIT extends AbstractIntegrationTest {

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "Admin@123";
    private static final String MOT_DE_PASSE = "MotDePasse!2026";

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Supprimer un élève révoque son compte ET ses jetons de rafraîchissement")
    void suppressionEleveRevoqueLeCompte() throws Exception {
        String token = loginAdmin();
        Compte compte = creerEleveAvecCompte(token);

        JsonNode session = connexionReussie(compte.username());

        mockMvc.perform(delete("/api/students/" + compte.id())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Le compte désactivé ne peut plus ouvrir de session...
        connexionRefusee(compte.username());
        // ...et la session déjà ouverte ne survit pas : le jeton de
        // rafraîchissement est supprimé, sinon il rejouait l'accès 7 jours.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + session.path("refreshToken").asText() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Radier un élève révoque son compte")
    void radiationEleveRevoqueLeCompte() throws Exception {
        String token = loginAdmin();
        Compte compte = creerEleveAvecCompte(token);
        connexionReussie(compte.username());

        mockMvc.perform(patch("/api/students/" + compte.id() + "/radiate")
                        .param("reason", "Déménagement")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RADIATED"));

        connexionRefusee(compte.username());
    }

    @Test
    @DisplayName("Réinscrire un élève révoqué rétablit l'accès avec le mot de passe d'origine")
    void reinscriptionRetablitLeCompte() throws Exception {
        String token = loginAdmin();
        Compte compte = creerEleveAvecCompte(token);
        connexionReussie(compte.username());

        mockMvc.perform(patch("/api/students/" + compte.id() + "/radiate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        connexionRefusee(compte.username());

        mockMvc.perform(patch("/api/students/" + compte.id() + "/reinscribe")
                        .param("reason", "Retour")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // Le mot de passe n'est pas régénéré : l'élève retrouve ses identifiants.
        connexionReussie(compte.username());
    }

    // ------------------------------------------------------------------ outils

    /** Un élève créé par l'API, avec le compte utilisateur généré par défaut. */
    private record Compte(long id, String username) {
    }

    private Compte creerEleveAvecCompte(String token) throws Exception {
        // Suffixe unique : le matricule, lui, est réattribué (count()+1) dès qu'un
        // dossier est supprimé, donc l'email dérivé du matricule entrerait en
        // collision d'une exécution à l'autre sur la base partagée.
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        String username = "it.revoc." + suffixe;
        String body = mockMvc.perform(post("/api/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Aicha\",\"lastName\":\"Kamdem\",\"gender\":\"FEMALE\""
                                + ",\"username\":\"" + username + "\",\"password\":\"" + MOT_DE_PASSE + "\""
                                + ",\"email\":\"it.revoc." + suffixe + "@school.local\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andReturn().getResponse().getContentAsString();
        return new Compte(objectMapper.readTree(body).path("data").path("id").asLong(), username);
    }

    private JsonNode connexionReussie(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + MOT_DE_PASSE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    /** Un compte révoqué est refusé par un message dédié (400), pas par un 500. */
    private void connexionRefusee(String username) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + MOT_DE_PASSE + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("désactivé")));
    }

    private String loginAdmin() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }
}
