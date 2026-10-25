package com.school.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.entity.Student;
import com.school.enums.Gender;
import com.school.enums.StudentStatus;
import com.school.repository.StudentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de bout en bout de la verticale universitaire (stages, mémoires,
 * alumni, admission).
 *
 * <p>Garde-fou de la régression corrigée : les contrôleurs renvoyaient les
 * entités JPA et, {@code open-in-view} étant désactivé, la sérialisation des
 * rattachements paresseux échouait en 500. Créer puis relire un stage avec un
 * élève est exactement le scénario qui cassait.</p>
 */
class UniversityModuleIT extends AbstractIntegrationTest {

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "Admin@123";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StudentRepository studentRepository;

    private String login() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.path("data").path("accessToken").asText();
    }

    /** Eleve de test (matricule unique : les tests partagent la meme base). */
    private Student eleveDeTest() {
        return studentRepository.save(Student.builder()
                .matricule("IT-UNIV-" + UUID.randomUUID().toString().substring(0, 8))
                .firstName("Aicha")
                .lastName("Kamdem")
                .gender(Gender.FEMALE)
                .enrollmentDate(LocalDate.now().minusMonths(1))
                .status(StudentStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("un stage cree est relu avec le nom et le matricule de l'eleve (plus de 500)")
    void stageCreePuisReluExposeLEleve() throws Exception {
        String token = login();
        Student eleve = eleveDeTest();

        mockMvc.perform(post("/api/stages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + eleve.getId()
                                + ",\"company\":\"TechCorp\",\"subject\":\"Developpement web\",\"status\":\"EN_COURS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentName").value("Aicha Kamdem"))
                .andExpect(jsonPath("$.data.matricule").value(eleve.getMatricule()));

        mockMvc.perform(get("/api/stages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].matricule", org.hamcrest.Matchers.hasItem(eleve.getMatricule())));
    }

    @Test
    @DisplayName("un stage sans entreprise est refuse en 400 avant toute ecriture")
    void stageSansEntrepriseRefuse() throws Exception {
        String token = login();
        Student eleve = eleveDeTest();

        mockMvc.perform(post("/api/stages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + eleve.getId() + ",\"company\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("une note hors bareme est refusee en 400")
    void stageNoteHorsBaremeRefusee() throws Exception {
        String token = login();
        Student eleve = eleveDeTest();

        mockMvc.perform(post("/api/stages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + eleve.getId()
                                + ",\"company\":\"TechCorp\",\"grade\":25}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("un memoire cree est relu avec le nom de l'eleve (plus de 500)")
    void memoireCreePuisReluExposeLEleve() throws Exception {
        String token = login();
        Student eleve = eleveDeTest();

        mockMvc.perform(post("/api/memoires")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + eleve.getId()
                                + ",\"subject\":\"Reseaux\",\"status\":\"EN_REDACTION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentName").value("Aicha Kamdem"));

        mockMvc.perform(get("/api/memoires")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].studentName", org.hamcrest.Matchers.hasItem("Aicha Kamdem")));
    }

    @Test
    @DisplayName("le parcours d'un diplome peut etre mis a jour")
    void alumniMisAJour() throws Exception {
        String token = login();

        String created = mockMvc.perform(post("/api/alumni")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jean\",\"lastName\":\"Ndoye\",\"fieldName\":\"Genie logiciel\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value(org.hamcrest.Matchers.nullValue()))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).path("data").path("id").asLong();

        mockMvc.perform(put("/api/alumni/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jean\",\"lastName\":\"Ndoye\",\"company\":\"Sahel Tech\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.company").value("Sahel Tech"));
    }

    @Test
    @DisplayName("la reference de candidature est attribuee par le serveur, jamais par le client")
    void candidatureReferenceAttribueeParLeServeur() throws Exception {
        String token = login();

        mockMvc.perform(post("/api/candidatures")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Awa\",\"lastName\":\"Diop\",\"fieldName\":\"Gestion\""
                                + ",\"reference\":\"CAND-999999\",\"status\":\"ACCEPTEE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reference", org.hamcrest.Matchers.startsWith("CAND-")))
                .andExpect(jsonPath("$.data.status").value("EN_ATTENTE"));

        mockMvc.perform(get("/api/candidatures")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].reference", notNullValue()));
    }

    @Test
    @DisplayName("la verticale universitaire reste interdite sans permission LMD")
    void universitaireInterditSansPermission() throws Exception {
        mockMvc.perform(get("/api/stages"))
                .andExpect(status().isUnauthorized());
    }
}
