package com.school.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.exception.BusinessException;
import com.school.service.AuditService;
import com.school.service.FileStorageService;
import com.school.service.SettingService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Réglages de la carte scolaire : lecture, écriture, emblème.
 *
 * <p>Deux invariants de sûreté sont verrouillés ici, parce qu'ils ne se voient
 * ni dans l'interface ni dans un PDF :</p>
 * <ol>
 *   <li><b>Une configuration refusée n'écrase jamais celle qui fonctionne.</b>
 *       La validation passe avant toute écriture.</li>
 *   <li><b>L'ancien emblème n'est supprimé qu'après</b> que le nouveau a été
 *       enregistré en base. Un téléversement interrompu laisse une carte
 *       complète ; l'ordre inverse laisserait une carte sans image.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class CardSettingsServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private SettingService settingService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private AuditService auditService;
    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private CardSettingsService service;

    private static JsonNode json(String raw) {
        try {
            return MAPPER.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ==================================================================
    // Lecture
    // ==================================================================

    @Nested
    @DisplayName("Lecture")
    class Lecture {

        @Test
        @DisplayName("aucun réglage enregistré : modèle d'origine")
        void aucunReglage() {
            when(settingService.value(CardSettingsService.CONFIG_KEY, "")).thenReturn("");
            assertThat(service.config().title).isEqualTo("CARTE D'IDENTITÉ SCOLAIRE");
            assertThat(service.isCustomised()).isFalse();
        }

        @Test
        @DisplayName("le JSON enregistré est appliqué")
        void jsonApplique() {
            when(settingService.value(CardSettingsService.CONFIG_KEY, ""))
                    .thenReturn("{\"title\": \"CARTE SCOLAIRE\", \"showBand\": false}");
            CardConfig c = service.config();
            assertThat(c.title).isEqualTo("CARTE SCOLAIRE");
            assertThat(c.showBand).isFalse();
            assertThat(service.isCustomised()).isTrue();
        }

        @Test
        @DisplayName("un JSON corrompu retombe sur le modèle d'origine au lieu de planter")
        void jsonCorrompu() {
            // Un document doit toujours pouvoir être produit : une valeur
            // inattendue en base ne doit pas rendre la carte générable.
            when(settingService.value(CardSettingsService.CONFIG_KEY, ""))
                    .thenReturn("{ ceci n'est pas du json");
            assertThat(service.config().title).isEqualTo("CARTE D'IDENTITÉ SCOLAIRE");
        }

        @Test
        @DisplayName("l'emblème absent renvoie une chaîne vide et aucun fichier")
        void emblemeAbsent() {
            lenient().when(settingService.value(CardSettingsService.EMBLEM_KEY, "")).thenReturn("");
            lenient().when(settingService.resolveUploadedFile(anyString())).thenReturn(Optional.empty());
            assertThat(service.emblemUrl()).isEmpty();
            assertThat(service.emblemFile()).isEmpty();
        }

        @Test
        @DisplayName("l'emblème enregistré est résolu sous le dossier d'uploads")
        void emblemeResolu() {
            Path fichier = Path.of("C:/tmp/uploads/school/emb.png");
            when(settingService.value(CardSettingsService.EMBLEM_KEY, ""))
                    .thenReturn("/uploads/school/emb.png");
            when(settingService.resolveUploadedFile("/uploads/school/emb.png"))
                    .thenReturn(Optional.of(fichier));
            assertThat(service.emblemFile()).contains(fichier);
        }
    }

    // ==================================================================
    // Écriture
    // ==================================================================

    @Nested
    @DisplayName("Écriture")
    class Ecriture {

        @Test
        @DisplayName("enregistre le JSON normalisé sous SCHOOL_CARD_CONFIG")
        @SuppressWarnings("unchecked")
        void enregistreNormalise() {
            service.update(json("{\"title\": \"CARTE SCOLAIRE\", \"cleInconnue\": 1}"), request);

            ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
            verify(settingService).updateAll(captor.capture(), eq(request));
            Map<String, String> ecrit = captor.getValue();
            assertThat(ecrit).containsOnlyKeys(CardSettingsService.CONFIG_KEY);

            JsonNode relu = CardConfigCodec.parse(ecrit.get(CardSettingsService.CONFIG_KEY));
            assertThat(relu.get("title").asText()).isEqualTo("CARTE SCOLAIRE");
            // Ce qui est stocké est exactement ce qui sera rendu : la clé
            // inconnue envoyée par le client n'est pas persistée.
            assertThat(relu.has("cleInconnue")).isFalse();
        }

        @Test
        @DisplayName("une configuration invalide est refusée et n'écrit rien")
        void refuseSansEcrire() {
            assertThatThrownBy(() -> service.update(json("{\"borderColor\": \"rouge\"}"), request))
                    .isInstanceOf(BusinessException.class);
            verify(settingService, never()).updateAll(any(), any());
        }

        @Test
        @DisplayName("la réinitialisation efface la clé de configuration")
        void reinitialise() {
            service.reset(request);
            verify(settingService).updateAll(Map.of(CardSettingsService.CONFIG_KEY, ""), request);
            verify(auditService).log(eq("SCHOOL_CARD_RESET"), eq("Setting"),
                    org.mockito.ArgumentMatchers.isNull(), anyString(), eq(request));
        }
    }

    // ==================================================================
    // Emblème
    // ==================================================================

    @Nested
    @DisplayName("Emblème")
    class Embleme {

        private MockMultipartFile fichier() {
            return new MockMultipartFile("file", "embleme.png", "image/png",
                    "contenu".getBytes(StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("enregistre le nouveau puis supprime l'ancien")
        void remplacePuisSupprime() {
            when(settingService.value(CardSettingsService.EMBLEM_KEY, ""))
                    .thenReturn("/uploads/school/ancien.png");
            when(fileStorageService.store(any(), eq("school")))
                    .thenReturn("/uploads/school/nouveau.png");

            String url = service.storeEmblem(fichier(), request);

            assertThat(url).isEqualTo("/uploads/school/nouveau.png");
            InOrder ordre = inOrder(settingService);
            ordre.verify(settingService).updateAll(
                    Map.of(CardSettingsService.EMBLEM_KEY, "/uploads/school/nouveau.png"), request);
            ordre.verify(settingService).deleteUploadedFile("/uploads/school/ancien.png");
        }

        @Test
        @DisplayName("aucun ancien emblème : rien à supprimer")
        void premierEmbleme() {
            when(settingService.value(CardSettingsService.EMBLEM_KEY, "")).thenReturn("");
            when(fileStorageService.store(any(), eq("school")))
                    .thenReturn("/uploads/school/nouveau.png");

            service.storeEmblem(fichier(), request);

            verify(settingService, never()).deleteUploadedFile(anyString());
        }

        @Test
        @DisplayName("un fichier vide est refusé sans rien enregistrer")
        void fichierVide() {
            when(settingService.value(CardSettingsService.EMBLEM_KEY, "")).thenReturn("");
            when(fileStorageService.store(any(), eq("school"))).thenReturn(null);

            assertThatThrownBy(() -> service.storeEmblem(fichier(), request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Aucun fichier");

            verify(settingService, never()).updateAll(any(), any());
        }

        @Test
        @DisplayName("la suppression efface la référence AVANT le fichier")
        void supprimeReferencePuisFichier() {
            // Si le fichier partait en premier et que l'écriture en base
            // échouait, la base pointerait vers un fichier absent : la carte
            // sortirait sans emblème et sans explication.
            when(settingService.value(CardSettingsService.EMBLEM_KEY, ""))
                    .thenReturn("/uploads/school/ancien.png");

            service.deleteEmblem(request);

            InOrder ordre = inOrder(settingService);
            ordre.verify(settingService).updateAll(
                    Map.of(CardSettingsService.EMBLEM_KEY, ""), request);
            ordre.verify(settingService).deleteUploadedFile("/uploads/school/ancien.png");
        }
    }
}
