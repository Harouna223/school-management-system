package com.school.pdf;

import com.lowagie.text.pdf.PdfReader;
import com.school.config.AppProperties;
import com.school.dto.response.StudentResponse;
import com.school.enums.Gender;
import com.school.service.QrCodeService;
import com.school.service.ReportService;
import com.school.service.SettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Rendu réel de la carte scolaire, vérifié dans le <b>flux PDF produit</b>.
 *
 * <p>Les tests de configuration prouvent qu'une valeur est acceptée ; ils ne
 * prouvent pas qu'elle atteint le document. Un réglage lu puis ignoré au moment
 * du dessin produirait exactement le même PDF qu'un réglage respecté — et c'est
 * le défaut le plus probable de ce genre de fonctionnalité.</p>
 *
 * <p>Le contenu de la page est donc relu depuis le PDF écrit (opérateurs de
 * texte) et comparé à ce qui est attendu : présence du titre, des lignes
 * d'en-tête, des libellés de champs, absence de ce qui a été masqué. Le format
 * de page est également épinglé : la carte doit rester au format CR80.</p>
 */
@ExtendWith(MockitoExtension.class)
class StudentCardRenderingTest {

    @Mock
    private AppProperties appProperties;
    @Mock
    private SettingService settingService;
    @Mock
    private QrCodeService qrCodeService;
    @Mock
    private SchoolDocumentTheme documentTheme;
    @Mock
    private CardSettingsService cardSettingsService;

    private ReportService service;

    @BeforeEach
    void setUp() {
        AppProperties.School school = new AppProperties.School();
        school.setName("École Les Palmiers");

        // lenient : selon la configuration testée, le logo n'est pas toujours
        // demandé (emblème « aucun », nom d'école masqué…). Un stub inutilisé
        // ne doit pas faire échouer un test qui ne le concerne pas.
        lenient().when(appProperties.getSchool()).thenReturn(school);
        lenient().when(settingService.value(eq(SettingService.SCHOOL_NAME), anyString()))
                .thenReturn("École Les Palmiers");
        lenient().when(settingService.value(eq(SettingService.SCHOOL_LOGO), anyString()))
                .thenReturn("");
        lenient().when(settingService.resolveUploadedFile(any())).thenReturn(Optional.empty());

        service = new ReportService(appProperties, settingService, qrCodeService,
                documentTheme, cardSettingsService);
    }

    private static StudentResponse eleve() {
        return StudentResponse.builder()
                .matricule("SE-2026-0042")
                .lastName("TRAORÉ")
                .firstName("Aminata")
                .birthDate(LocalDate.of(2013, 4, 12))
                .birthPlace("Bamako")
                .gender(Gender.FEMALE)
                .className("6ème A")
                .build();
    }

    /** Contenu de la page 1, décodé en ISO-8859-1 (correspondance octet → caractère). */
    private static String pageContent(MockHttpServletResponse response) throws Exception {
        PdfReader reader = new PdfReader(response.getContentAsByteArray());
        try {
            return new String(reader.getPageContent(1), StandardCharsets.ISO_8859_1);
        } finally {
            reader.close();
        }
    }

    private static PdfReader reader(MockHttpServletResponse response) throws Exception {
        return new PdfReader(response.getContentAsByteArray());
    }

    /**
     * Nombre d'images référencées par la page.
     *
     * <p>Plus fiable que de chercher l'opérateur {@code Do} dans le flux : un
     * « Do » peut apparaître dans une chaîne de texte, pas un XObject.</p>
     */
    private static int imageCount(MockHttpServletResponse response) throws Exception {
        PdfReader r = reader(response);
        try {
            com.lowagie.text.pdf.PdfDictionary resources =
                    r.getPageN(1).getAsDict(com.lowagie.text.pdf.PdfName.RESOURCES);
            if (resources == null) {
                return 0;
            }
            com.lowagie.text.pdf.PdfDictionary xobjects =
                    resources.getAsDict(com.lowagie.text.pdf.PdfName.XOBJECT);
            return xobjects == null ? 0 : xobjects.size();
        } finally {
            r.close();
        }
    }

    // ==================================================================
    // Format et en-tête HTTP
    // ==================================================================

    @Nested
    @DisplayName("Format et en-tête HTTP")
    class Format {

        @Test
        @DisplayName("la carte reste au format CR80 (85,6 × 54 mm)")
        void formatCr80() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();
            service.studentCardPreviewPdf(response, eleve(), CardConfig.defaults());

            PdfReader r = reader(response);
            try {
                assertThat(r.getPageSize(1).getWidth()).isCloseTo(242.65f, within(0.5f));
                assertThat(r.getPageSize(1).getHeight()).isCloseTo(153.07f, within(0.5f));
                assertThat(r.getNumberOfPages()).isEqualTo(1);
            } finally {
                r.close();
            }
            assertThat(response.getContentType()).isEqualTo("application/pdf");
        }

        @Test
        @DisplayName("l'aperçu s'affiche dans le navigateur, la carte réelle se télécharge")
        void disposition() throws Exception {
            MockHttpServletResponse apercu = new MockHttpServletResponse();
            service.studentCardPreviewPdf(apercu, eleve(), CardConfig.defaults());
            assertThat(apercu.getHeader("Content-Disposition")).startsWith("inline");

            when(cardSettingsService.config()).thenReturn(CardConfig.defaults());
            MockHttpServletResponse carte = new MockHttpServletResponse();
            service.schoolIdCardPdf(carte, eleve());
            assertThat(carte.getHeader("Content-Disposition"))
                    .isEqualTo("attachment; filename=\"carte-SE-2026-0042.pdf\"");
        }

        @Test
        @DisplayName("l'aperçu porte la mention « sans valeur officielle », la carte non")
        void mentionApercu() throws Exception {
            MockHttpServletResponse apercu = new MockHttpServletResponse();
            service.studentCardPreviewPdf(apercu, eleve(), CardConfig.defaults());
            // « APER… » : le « Ç » est hors ASCII, on s'arrête avant.
            assertThat(pageContent(apercu)).contains("APER");

            when(cardSettingsService.config()).thenReturn(CardConfig.defaults());
            MockHttpServletResponse carte = new MockHttpServletResponse();
            service.schoolIdCardPdf(carte, eleve());
            assertThat(pageContent(carte)).doesNotContain("APER");
        }
    }

    // ==================================================================
    // Modèle d'origine
    // ==================================================================

    @Nested
    @DisplayName("Modèle d'origine")
    class ModeleOrigine {

        private MockHttpServletResponse render(CardConfig config) throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();
            service.studentCardPreviewPdf(response, eleve(), config);
            return response;
        }

        @Test
        @DisplayName("imprime le titre, l'en-tête institutionnel et le nom de l'école")
        void contenuComplet() throws Exception {
            String page = pageContent(render(CardConfig.defaults()));
            assertThat(page).contains("CARTE D'IDENTIT");
            assertThat(page).contains("Peuple");
            assertThat(page).contains("Minist");
            assertThat(page).contains("COLE LES PALMIERS"); // nom passé en majuscules
            assertThat(page).contains("Signature et cachet du Directeur");
        }

        @Test
        @DisplayName("imprime les sept champs d'identité")
        void septChamps() throws Exception {
            String page = pageContent(render(CardConfig.defaults()));
            assertThat(page).contains("Nom : ");
            // Les parenthèses du libellé sont échappées dans le flux PDF
            // (« Prénom\(s\) : »), et le « é » est hors ASCII : on cherche
            // donc la forme exacte telle qu'elle est écrite.
            assertThat(page).contains("Prénom\\(s\\) : ");
            assertThat(page).contains("Matricule : ");
            assertThat(page).contains("Date de naissance : ");
            assertThat(page).contains("Lieu de naissance : ");
            assertThat(page).contains("Sexe : ");
            assertThat(page).contains("Classe : ");
            // FÉMININ : même remarque pour le É.
            assertThat(page).contains("MININ");
        }

        @Test
        @DisplayName("dessine l'image de la pastille")
        void imagePresente() throws Exception {
            // Le rendu d'origine place le logo de l'école en haut à gauche :
            // une image XObject doit donc être référencée par la page.
            assertThat(imageCount(render(CardConfig.defaults()))).isEqualTo(1);
        }
    }

    // ==================================================================
    // Réglages appliqués
    // ==================================================================

    @Nested
    @DisplayName("Réglages appliqués")
    class ReglagesAppliques {

        private MockHttpServletResponse render(CardConfig config) throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();
            service.studentCardPreviewPdf(response, eleve(), config);
            return response;
        }

        @Test
        @DisplayName("l'en-tête institutionnel masqué disparaît du document")
        void enteteMasque() throws Exception {
            String page = pageContent(render(CardConfig.defaults().toBuilder()
                    .showHeader(false).build()));
            assertThat(page).doesNotContain("Peuple");
            assertThat(page).doesNotContain("Minist");
            // Le titre reste : c'est lui qui identifie le document.
            assertThat(page).contains("CARTE D'IDENTIT");
        }

        @Test
        @DisplayName("un titre personnalisé remplace le titre d'origine")
        void titrePersonnalise() throws Exception {
            String page = pageContent(render(CardConfig.defaults().toBuilder()
                    .title("CARTE SCOLAIRE 2026").build()));
            assertThat(page).contains("CARTE SCOLAIRE 2026");
            assertThat(page).doesNotContain("CARTE D'IDENTIT");
        }

        @Test
        @DisplayName("seuls les champs retenus sont imprimés")
        void champsFiltres() throws Exception {
            String page = pageContent(render(CardConfig.defaults().toBuilder()
                    .fields(List.of(CardConfig.Field.LAST_NAME, CardConfig.Field.CLASS_NAME))
                    .build()));
            assertThat(page).contains("Nom : ");
            assertThat(page).contains("Classe : ");
            assertThat(page).doesNotContain("Matricule : ");
            assertThat(page).doesNotContain("Sexe : ");
            assertThat(page).doesNotContain("Lieu de naissance : ");
        }

        @Test
        @DisplayName("le pied de carte vidé n'est pas dessiné")
        void piedVide() throws Exception {
            String page = pageContent(render(CardConfig.defaults().toBuilder()
                    .footerText("").build()));
            assertThat(page).doesNotContain("Signature et cachet");
        }

        @Test
        @DisplayName("le nom de l'établissement peut être masqué")
        void nomMasque() throws Exception {
            String page = pageContent(render(CardConfig.defaults().toBuilder()
                    .showSchoolName(false).build()));
            assertThat(page).doesNotContain("COLE LES PALMIERS");
            assertThat(page).contains("CARTE D'IDENTIT");
        }

        @Test
        @DisplayName("emblème « aucun » : plus aucune image n'est dessinée")
        void emblemeAucun() throws Exception {
            MockHttpServletResponse response = render(CardConfig.defaults().toBuilder()
                    .emblemSource(CardConfig.EmblemSource.NONE)
                    .showSchoolLogo(false)
                    .build());
            assertThat(imageCount(response)).isZero();
        }

        @Test
        @DisplayName("l'emblème choisi mais absent retombe sur le logo de l'école")
        void emblemeTeleverse() throws Exception {
            // Aucun emblème n'a été téléversé (Optional.empty) : la pastille
            // ne doit pas rester vide sur un document imprimé, sinon la carte
            // se lit comme un défaut d'impression.
            MockHttpServletResponse response = render(CardConfig.defaults().toBuilder()
                    .emblemSource(CardConfig.EmblemSource.EMBLEM)
                    .build());
            assertThat(imageCount(response)).isEqualTo(1);
        }

        @Test
        @DisplayName("le logo en bas à droite s'ajoute à l'emblème")
        void logoEnPlus() throws Exception {
            MockHttpServletResponse response = render(CardConfig.defaults().toBuilder()
                    .showSchoolLogo(true)
                    .build());
            assertThat(imageCount(response)).isEqualTo(2);
        }
    }
}
