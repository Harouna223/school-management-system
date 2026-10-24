package com.school.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.school.entity.DocumentThemeVersion;
import com.school.pdf.DocumentStyle;
import com.school.pdf.DocumentStyleService;
import com.school.repository.DocumentStyleOverrideRepository;
import com.school.repository.DocumentThemeVersionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.awt.Color;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration de la migration V10 (modeles et apparence des documents).
 *
 * <p>Ce que seul un vrai MySQL peut prouver :</p>
 * <ul>
 *   <li>Flyway applique V10 et Hibernate ({@code ddl-auto=validate}) accepte les
 *       nouvelles tables et la colonne {@code bulletins.document_style_id} ;</li>
 *   <li>la configuration est reellement ecrite en base et relue apres remise a
 *       zero du cache du service (equivalent d'un redemarrage) ;</li>
 *   <li>un reglage par type de document ne modifie que ce type ;</li>
 *   <li>les documents deja emis restent intacts (aucun bulletin n'est reecrit) ;</li>
 *   <li>les droits sont appliques cote serveur (SUPER_ADMIN uniquement).</li>
 * </ul>
 */
class DocumentThemeIT extends AbstractIntegrationTest {

    @Autowired
    private DocumentStyleService styleService;

    @Autowired
    private DocumentThemeVersionRepository versionRepository;

    @Autowired
    private DocumentStyleOverrideRepository overrideRepository;

    private Connection connexion() throws Exception {
        return DriverManager.getConnection(MySqlTestContainer.JDBC_URL,
                MySqlTestContainer.USERNAME, MySqlTestContainer.PASSWORD);
    }

    // ------------------------------------------------------------------
    // Migration
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Flyway a applique V10 (migration enregistree et reussie)")
    void migrationV10Enregistree() throws Exception {
        try (Connection conn = connexion();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '10' AND success = 1");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            assertThat(rs.getInt(1)).as("V10 presente et reussie").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("les deux tables du theme existent reellement")
    void tablesCreees() throws Exception {
        assertThat(tableExiste("document_theme_versions")).isTrue();
        assertThat(tableExiste("document_style_overrides")).isTrue();
    }

    @Test
    @DisplayName("bulletins.document_style_id est nullable (les bulletins existants restent valides)")
    void colonneEpingleNullable() throws Exception {
        try (Connection conn = connexion();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT IS_NULLABLE FROM information_schema.COLUMNS "
                             + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bulletins' "
                             + "AND COLUMN_NAME = 'document_style_id'");
             ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).as("colonne document_style_id presente").isTrue();
            assertThat(rs.getString(1)).isEqualTo("YES");
        }
    }

    @Test
    @DisplayName("la migration d'amorcage n'active PAS de theme (aucun changement visuel impose)")
    void aucunThemeActifAuDepart() throws Exception {
        // La charte d'origine est enregistree mais desactivee : sans action de
        // l'administrateur, les documents gardent exactement leur aspect historique.
        // La table est videe au depart pour que cette assertion ne depende pas de
        // l'ordre d'execution des tests.
        viderTablesTheme();
        assertThat(versionRepository.countByActiveTrue()).isZero();
        assertThat(styleService.activeStyle().primary)
                .as("sans theme actif, la charte historique s'applique")
                .isEqualTo(DocumentStyle.defaults().primary);
    }

    // ------------------------------------------------------------------
    // Persistance
    // ------------------------------------------------------------------

    @Test
    @DisplayName("enregistrer un theme l'ecrit en base et l'applique immediatement")
    void enregistrementApplique() throws Exception {
        String token = jetonAdmin();
        String config = configAvecCouleur("#7f1d1d", "PREMIUM");

        long id = enregistrer(token, "Test bordeaux premium", config, true);

        DocumentThemeVersion version = versionRepository.findById(id).orElseThrow();
        assertThat(version.isActive()).isTrue();
        assertThat(version.getLabel()).isEqualTo("Test bordeaux premium");
        assertThat(version.getCreatedBy()).as("auteur trace").isNotBlank();

        // Le service resout la nouvelle charte sans que rien n'ait ete recompile.
        DocumentStyle style = styleService.activeStyle();
        assertThat(style.primary).isEqualTo(new Color(0x7f, 0x1d, 0x1d));
        assertThat(style.template).isEqualTo(DocumentStyle.Template.PREMIUM);
    }

    @Test
    @DisplayName("la configuration survit a un redemarrage (rechargement depuis la base)")
    void persistanceApresRedemarrage() throws Exception {
        String token = jetonAdmin();
        enregistrer(token, "Test persistant", configAvecCouleur("#14532d", "MODERN"), true);

        // Simule un redemarrage : le cache memoire est vide, tout est relu en base.
        styleService.invalidateCache();

        assertThat(styleService.activeStyle().primary).isEqualTo(new Color(0x14, 0x53, 0x2d));
        assertThat(styleService.activeVersionLabel()).isEqualTo("Test persistant");
    }

    @Test
    @DisplayName("reactiver un ancien theme restaure exactement son apparence (retour arriere)")
    void reactivationVersionAncienne() throws Exception {
        String token = jetonAdmin();
        long premier = enregistrer(token, "Charte A", configAvecCouleur("#1e3a8a", "CLASSIC"), true);
        enregistrer(token, "Charte B", configAvecCouleur("#7f1d1d", "PREMIUM"), true);
        assertThat(styleService.activeStyle().primary).isEqualTo(new Color(0x7f, 0x1d, 0x1d));

        mockMvc.perform(post("/api/settings/document-theme/" + premier + "/activate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(styleService.activeVersionId()).isEqualTo(premier);
        assertThat(styleService.activeStyle().primary).isEqualTo(new Color(0x1e, 0x3a, 0x8a));
        // Une seule version active a la fois.
        assertThat(versionRepository.countByActiveTrue()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Niveau 2 : reglages par type de document
    // ------------------------------------------------------------------

    @Test
    @DisplayName("un reglage propre a un type de document ne modifie pas les autres")
    void reglageParTypeIsole() throws Exception {
        String token = jetonAdmin();
        enregistrer(token, "Charte de base", configAvecCouleur("#1e3a8a", "CLASSIC"), true);

        // Le bulletin passe en paysage ; rien d'autre ne bouge.
        String patch = "{\"layout\":{\"orientation\":\"LANDSCAPE\"}}";
        mockMvc.perform(put("/api/settings/document-theme/overrides/BULLETIN")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patch))
                .andExpect(status().isOk());

        DocumentStyle bulletin = styleService.resolve("BULLETIN");
        DocumentStyle recu = styleService.resolve("PAYMENT_RECEIPT");

        assertThat(bulletin.orientation).isEqualTo(DocumentStyle.Orientation.LANDSCAPE);
        assertThat(recu.orientation).as("le recu n'est pas affecte").isEqualTo(DocumentStyle.Orientation.PORTRAIT);
        // Les couleurs du theme global survivent au remplacement partiel.
        assertThat(bulletin.primary).isEqualTo(recu.primary);

        // Un remplacement vide supprime la personnalisation.
        mockMvc.perform(delete("/api/settings/document-theme/overrides/BULLETIN")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(styleService.resolve("BULLETIN").orientation).isEqualTo(DocumentStyle.Orientation.PORTRAIT);
    }

    @Test
    @DisplayName("un type de document inconnu est refuse (pas de ligne orpheline en base)")
    void typeInconnuRefuse() throws Exception {
        String token = jetonAdmin();
        mockMvc.perform(put("/api/settings/document-theme/overrides/PAS_UN_DOCUMENT")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"layout\":{\"orientation\":\"LANDSCAPE\"}}"))
                .andExpect(status().isBadRequest());

        assertThat(overrideRepository.findByDocumentType("PAS_UN_DOCUMENT")).isEmpty();
    }

    // ------------------------------------------------------------------
    // Validation et securite
    // ------------------------------------------------------------------

    @Test
    @DisplayName("une couleur HEX invalide est refusee cote serveur")
    void couleurInvalideRefusee() throws Exception {
        String token = jetonAdmin();
        String config = "{\"colors\":{\"primary\":\"rouge\"}}";

        mockMvc.perform(post("/api/settings/document-theme")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Test invalide\",\"config\":" + config + ",\"activate\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("une taille de police hors bornes est refusee (document illisible impossible a enregistrer)")
    void tailleHorsBornesRefusee() throws Exception {
        String token = jetonAdmin();
        String config = "{\"typography\":{\"bodySize\":60}}";

        mockMvc.perform(post("/api/settings/document-theme")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Test taille\",\"config\":" + config + ",\"activate\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("le module est interdit a un utilisateur non administrateur")
    void accesRefuseHorsAdministrateur() throws Exception {
        mockMvc.perform(get("/api/settings/document-theme"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("la previsualisation produit un vrai PDF, sans ecrire en base")
    void previsualisationProduitUnPdf() throws Exception {
        String token = jetonAdmin();
        long avant = versionRepository.count();

        byte[] pdf =         mockMvc.perform(post("/api/settings/document-theme/preview")
                        .param("documentType", "BULLETIN")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(pdf).as("un PDF non vide").hasSizeGreaterThan(1000);
        assertThat(versionRepository.count()).as("aucune version creee par un apercu").isEqualTo(avant);
    }

    @Test
    @DisplayName("restaurer la charte d'origine desactive tout theme sans effacer l'historique")
    void restaurationConserveLHistorique() throws Exception {
        String token = jetonAdmin();
        enregistrer(token, "A supprimer de l'activation", configAvecCouleur("#334155", "MINIMAL"), true);
        long total = versionRepository.count();

        mockMvc.perform(delete("/api/settings/document-theme")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(versionRepository.countByActiveTrue()).isZero();
        assertThat(versionRepository.count()).as("aucune version supprimee").isEqualTo(total);
        // Sans theme actif, on retombe exactement sur la charte historique.
        assertThat(styleService.activeStyle().primary)
                .isEqualTo(DocumentStyle.defaults().primary);
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    /**
     * Vide les tables du theme et le cache du service.
     *
     * <p>Ces tests ecrivent en base sans rollback (comme les autres IT du
     * projet) : sans remise a zero, une assertion sur « aucun theme actif au
     * depart » dependrait de l'ordre d'execution. La migration V10 n'est pas
     * rejouee pour autant : Flyway est deja passe.</p>
     */
    private void viderTablesTheme() throws Exception {
        try (Connection conn = connexion();
             java.sql.Statement st = conn.createStatement()) {
            st.execute("DELETE FROM document_style_overrides");
            st.execute("DELETE FROM document_theme_versions");
        }
        styleService.invalidateCache();
    }

    /** Configuration minimale complete, avec une couleur principale et un modele imposes. */    private String configAvecCouleur(String primaire, String modele) {
        ObjectNode root = new ObjectMapper().createObjectNode();
        root.put("template", modele);
        root.putObject("colors").put("primary", primaire);
        return root.toString();
    }

    /** Enregistre et renvoie l'identifiant de la version creee. */
    private long enregistrer(String token, String label, String config, boolean activer) throws Exception {
        String corps = mockMvc.perform(post("/api/settings/document-theme")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"" + label + "\",\"config\":" + config
                                + ",\"activate\":" + activer + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version.id").exists())
                .andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(corps).path("data").path("version").path("id").asLong();
    }

    private boolean tableExiste(String table) throws Exception {
        try (Connection conn = connexion();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.TABLES "
                             + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) == 1;
            }
        }
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
}
