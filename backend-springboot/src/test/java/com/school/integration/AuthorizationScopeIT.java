package com.school.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Périmètre réel d'un compte ENSEIGNANT.
 *
 * <p>Avant correction, un enseignant affecté à une seule classe pouvait lister
 * les élèves de tout l'établissement, lire les évaluations d'une autre classe,
 * y saisir des notes et pointer des présences pour n'importe quel élève. Ces
 * contrôles vivent dans {@code AccessControlService} et les services métier :
 * seul un test HTTP sur la vraie base (avec un vrai jeton, de vraies
 * affectations et de vraies transactions) prouve qu'ils s'appliquent.</p>
 *
 * <p>Ce fichier couvre aussi la révocation des sessions : changement de mot de
 * passe, désactivation puis réactivation du compte. Le jeton de rafraîchissement
 * délivré avant ces opérations doit rester définitivement inutilisable.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthorizationScopeIT extends AbstractIntegrationTest {

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "Admin@123";
    private static final String AUTH = "Authorization";
    private static final String MOT_DE_PASSE = "MotDePasse!2026";
    private static final String NOUVEAU_MOT_DE_PASSE = "NouveauMotDePasse!2026";
    private static final String DATE_POINTAGE = "2026-09-15";

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String anneeLabel;

    private long classeA;
    private long classeB;
    private long matiereId;
    private long eleveA;
    private long eleveB;
    private long examenA;
    private long examenB;

    private String profUsername;
    private long profTeacherId;
    private String sessionUsername;

    @BeforeAll
    void preparerLeContexte() throws Exception {
        // Les fixtures utilisent HTTP : il faut un MockMvc avant les tests
        // (le @BeforeEach du socle le reconstruira pour chacun d'eux).
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        adminToken = loginData(ADMIN, ADMIN_PASSWORD).path("accessToken").asText();
        anneeLabel = assurerAnneeScolaire();
        long niveauId = premierNiveau();

        String sfx = UUID.randomUUID().toString().substring(0, 8);
        classeA = creerClasse("IT-A-" + sfx, "ITA" + sfx, niveauId);
        classeB = creerClasse("IT-B-" + sfx, "ITB" + sfx, niveauId);
        matiereId = creerMatiere("Maths IT " + sfx, "MIT" + sfx);

        profUsername = "it.authz.prof." + sfx;
        profTeacherId = creerEnseignant(profUsername);
        affecter(profTeacherId, matiereId, classeA);

        eleveA = creerEleve("Aicha", "ClasseA" + sfx, classeA);
        eleveB = creerEleve("Bintou", "ClasseB" + sfx, classeB);

        examenA = creerEvaluation("Devoir A " + sfx, classeA);
        examenB = creerEvaluation("Devoir B " + sfx, classeB);

        sessionUsername = "it.authz.session." + sfx;
        creerEnseignant(sessionUsername);
    }

    // ------------------------------------------------------------- périmètre

    @Test
    @DisplayName("un enseignant ne liste et ne consulte que les élèves de ses classes")
    void enseignantLimiteAuxElevesDeSesClasses() throws Exception {
        String token = jetonEnseignant();

        // Classe hors périmètre demandée explicitement : refus, pas une liste vide.
        mockMvc.perform(avecJeton(get("/api/students"), token)
                        .param("classId", String.valueOf(classeB)))
                .andExpect(status().isForbidden());

        // Sans filtre : uniquement les élèves de sa classe (jamais l'établissement).
        String listeAvecFiltre = mockMvc.perform(avecJeton(get("/api/students"), token)
                        .param("classId", String.valueOf(classeA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode page = objectMapper.readTree(listeAvecFiltre).path("data");
        assertThat(page.path("totalElements").asLong()).isEqualTo(1);
        assertThat(page.path("content").get(0).path("id").asLong()).isEqualTo(eleveA);

        String listeSansFiltre = mockMvc.perform(avecJeton(get("/api/students"), token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode complete = objectMapper.readTree(listeSansFiltre).path("data");
        assertThat(complete.path("totalElements").asLong()).isEqualTo(1);
        assertThat(complete.path("content").get(0).path("classId").asLong()).isEqualTo(classeA);

        // Accès direct par identifiant : l'élève d'une autre classe est refusé.
        mockMvc.perform(avecJeton(get("/api/students/" + eleveB), token))
                .andExpect(status().isForbidden());
        mockMvc.perform(avecJeton(get("/api/students/" + eleveA), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(eleveA));
    }

    @Test
    @DisplayName("un enseignant ne lit pas les évaluations d'une autre classe")
    void enseignantNeLitPasLesEvaluationsDUneAutreClasse() throws Exception {
        String token = jetonEnseignant();

        mockMvc.perform(avecJeton(get("/api/exams/class/" + classeB), token))
                .andExpect(status().isForbidden());
        mockMvc.perform(avecJeton(get("/api/exams/" + examenB), token))
                .andExpect(status().isForbidden());
        mockMvc.perform(avecJeton(get("/api/exams"), token)
                        .param("classId", String.valueOf(classeB)))
                .andExpect(status().isForbidden());

        mockMvc.perform(avecJeton(get("/api/exams/class/" + classeA), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(examenA));

        // Recherche sans filtre : seulement les évaluations de sa classe.
        String corps = mockMvc.perform(avecJeton(get("/api/exams"), token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode page = objectMapper.readTree(corps).path("data");
        assertThat(page.path("totalElements").asLong()).isEqualTo(1);
        assertThat(page.path("content").get(0).path("id").asLong()).isEqualTo(examenA);
    }

    @Test
    @DisplayName("un enseignant ne saisit de note que dans sa classe et pour ses élèves")
    void enseignantNECritQueDansSonPerimetre() throws Exception {
        String token = jetonEnseignant();

        // Évaluation d'une autre classe : refus, même avec GRADE_WRITE.
        mockMvc.perform(avecJeton(post("/api/exams/grades"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(note(eleveB, examenB, 12)))
                .andExpect(status().isForbidden());

        // Évaluation de sa classe mais élève d'une autre classe : refus.
        mockMvc.perform(avecJeton(post("/api/exams/grades"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(note(eleveB, examenA, 12)))
                .andExpect(status().isForbidden());

        // Élève de sa classe sur son évaluation : accepté.
        mockMvc.perform(avecJeton(post("/api/exams/grades"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(note(eleveA, examenA, 12)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value(12));
    }

    @Test
    @DisplayName("le pointage refuse une classe hors périmètre et un élève étranger à la classe")
    void pointageRefuseHorsPerimetre() throws Exception {
        String token = jetonEnseignant();

        mockMvc.perform(avecJeton(post("/api/attendances"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pointage(classeB, eleveB)))
                .andExpect(status().isForbidden());

        // Classe autorisée mais élève d'une autre classe : erreur métier explicite.
        mockMvc.perform(avecJeton(post("/api/attendances"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pointage(classeA, eleveB)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers
                        .containsString("n'appartient pas à la classe")));

        mockMvc.perform(avecJeton(post("/api/attendances"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pointage(classeA, eleveA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attendance[0].studentId").value(eleveA));
    }

    // ---------------------------------------------------------------- sessions

    @Test
    @DisplayName("changement de mot de passe puis désactivation : les anciens jetons restent morts")
    void changerLeMotDePasseOuDesactiverRevoqueLesSessions() throws Exception {
        JsonNode session1 = loginData(sessionUsername, MOT_DE_PASSE);
        JsonNode session2 = loginData(sessionUsername, MOT_DE_PASSE);

        mockMvc.perform(post("/api/auth/change-password")
                        .header(AUTH, bearer(session1.path("accessToken").asText()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"%s"}
                                """.formatted(MOT_DE_PASSE, NOUVEAU_MOT_DE_PASSE)))
                .andExpect(status().isOk());

        // TOUTES les sessions ouvertes sont coupées, pas seulement la courante.
        rafraichissementRefuse(session1.path("refreshToken").asText());
        rafraichissementRefuse(session2.path("refreshToken").asText());

        // L'ancien mot de passe ne fonctionne plus, le nouveau oui.
        refuserConnexion(sessionUsername, MOT_DE_PASSE);
        JsonNode session3 = loginData(sessionUsername, NOUVEAU_MOT_DE_PASSE);

        long userId = utilisateurId(sessionUsername);
        changerEtatCompte(userId, false);
        rafraichissementRefuse(session3.path("refreshToken").asText());

        // Réactivation : le jeton supprimé à la désactivation ne redevient PAS valide.
        changerEtatCompte(userId, true);
        rafraichissementRefuse(session3.path("refreshToken").asText());

        loginData(sessionUsername, NOUVEAU_MOT_DE_PASSE);
    }

    // ------------------------------------------------------------------ outils

    private MockHttpServletRequestBuilder avecJeton(MockHttpServletRequestBuilder requete, String token) {
        return requete.header(AUTH, bearer(token));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String jetonEnseignant() throws Exception {
        return loginData(profUsername, MOT_DE_PASSE).path("accessToken").asText();
    }

    private JsonNode loginData(String username, String password) throws Exception {
        String corps = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corps).path("data");
    }

    private void refuserConnexion(String username, String password) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isUnauthorized());
    }

    private void rafraichissementRefuse(String refreshToken) throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    private void changerEtatCompte(long userId, boolean enabled) throws Exception {
        mockMvc.perform(patch("/api/users/" + userId + "/enabled")
                        .header(AUTH, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":" + enabled + "}"))
                .andExpect(status().isOk());
    }

    private long utilisateurId(String username) throws Exception {
        String corps = mockMvc.perform(avecJeton(get("/api/users"), adminToken)
                        .param("search", username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode utilisateur : objectMapper.readTree(corps).path("data").path("content")) {
            if (username.equals(utilisateur.path("username").asText())) {
                return utilisateur.path("id").asLong();
            }
        }
        throw new IllegalStateException("Utilisateur introuvable : " + username);
    }

    private String assurerAnneeScolaire() throws Exception {
        String corps = mockMvc.perform(avecJeton(get("/api/academic-years"), adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode annees = objectMapper.readTree(corps).path("data");
        if (!annees.isEmpty()) {
            return annees.get(0).path("label").asText();
        }
        String creee = mockMvc.perform(avecJeton(post("/api/academic-years"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"2026-2027","startDate":"2026-09-01",
                                 "endDate":"2027-07-31","isCurrent":true}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creee).path("data").path("label").asText();
    }

    private long premierNiveau() throws Exception {
        String corps = mockMvc.perform(avecJeton(get("/api/classes/levels"), adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode niveaux = objectMapper.readTree(corps).path("data");
        assertThat(niveaux.size()).as("niveaux initiaux créés au démarrage").isGreaterThan(0);
        return niveaux.get(0).path("id").asLong();
    }

    private long creerClasse(String nom, String code, long niveauId) throws Exception {
        return poster("/api/classes", """
                {"name":"%s","code":"%s","levelId":%d,"capacity":30}
                """.formatted(nom, code, niveauId));
    }

    private long creerMatiere(String nom, String code) throws Exception {
        return poster("/api/subjects", """
                {"name":"%s","code":"%s","coefficient":1}
                """.formatted(nom, code));
    }

    private long creerEnseignant(String username) throws Exception {
        return poster("/api/teachers", """
                {"firstName":"Jean","lastName":"Test","gender":"MALE",
                 "hireDate":"2026-01-05","contractType":"CDI","salary":250000,
                 "createUserAccount":true,"username":"%s","password":"%s"}
                """.formatted(username, MOT_DE_PASSE));
    }

    private long creerEleve(String prenom, String nom, long classId) throws Exception {
        return poster("/api/students", """
                {"firstName":"%s","lastName":"%s","gender":"FEMALE","classId":%d}
                """.formatted(prenom, nom, classId));
    }

    private long creerEvaluation(String nom, long classId) throws Exception {
        return poster("/api/exams", """
                {"name":"%s","type":"DEVOIR","term":"T1","academicYear":"%s",
                 "classId":%d,"subjectId":%d,"examDate":"2026-10-15","coefficient":1}
                """.formatted(nom, anneeLabel, classId, matiereId));
    }

    private long poster(String url, String json) throws Exception {
        String corps = mockMvc.perform(avecJeton(post(url), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corps).path("data").path("id").asLong();
    }

    private void affecter(long teacherId, long subjectId, long classId) throws Exception {
        mockMvc.perform(avecJeton(post("/api/subjects/assignments"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teacherId":%d,"subjectId":%d,"classId":%d}
                                """.formatted(teacherId, subjectId, classId)))
                .andExpect(status().isOk());
    }

    private String note(long studentId, long examId, int valeur) {
        return """
                {"studentId":%d,"examId":%d,"value":%d,"maxValue":20}
                """.formatted(studentId, examId, valeur);
    }

    private String pointage(long classId, long studentId) {
        return """
                {"classId":%d,"date":"%s","entries":[{"studentId":%d,"status":"PRESENT"}]}
                """.formatted(classId, DATE_POINTAGE, studentId);
    }
}
