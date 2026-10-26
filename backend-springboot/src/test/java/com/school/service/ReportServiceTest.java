package com.school.service;

import com.school.config.AppProperties;
import com.school.pdf.SchoolDocumentTheme;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Génération de documents (PDF / Excel).
 *
 * <p>Le rendu PDF est délégué à {@link SchoolDocumentTheme} ; on le <b>mocke</b> pour verrouiller
 * les <b>décisions</b> prises par ce service (sous-titre composé ou non, ligne de total ajoutée ou
 * non, zébrage activé) plutôt que la mise en page. Mais {@code init()} est réimplémenté par un
 * <b>vrai</b> {@code PdfWriter} : le document est donc réellement écrit, ce qui prouve que le
 * service ne se contente pas d'appeler des méthodes — il produit bien un PDF.</p>
 *
 * <p>Deux invariants « silencieux » sont épinglés ici :</p>
 * <ol>
 *   <li>{@code sanitize()} est appliqué au <b>titre</b> pour fabriquer un nom de fichier et un nom
 *       d'onglet. Un titre contenant {@code ../../} doit être neutralisé — c'est un en-tête HTTP
 *       {@code Content-Disposition} et un nom d'onglet Excel, pas un chemin.</li>
 *   <li>Une cellule <b>nulle</b> devient une chaîne <b>vide</b> et non une cellule absente : sinon
 *       les colonnes se décalent silencieusement dans le fichier livré.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private AppProperties appProperties;
    @Mock
    private SettingService settingService;
    @Mock
    private QrCodeService qrCodeService;
    @Mock
    private SchoolDocumentTheme documentTheme;
    @Mock
    private com.school.pdf.CardSettingsService cardSettingsService;
    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private ReportService service;

    private ByteArrayOutputStream excelBytes;
    private ByteArrayOutputStream pdfBytes;

    @BeforeEach
    void setUp() throws Exception {
        excelBytes = new ByteArrayOutputStream();
        pdfBytes = new ByteArrayOutputStream();
        // Stub global : seuls les tests Excel le consomment.
        lenient().when(response.getOutputStream()).thenAnswer(inv -> sink(excelBytes));
    }

    /** Un flux de sortie écrivable, adossé à un tableau d'octets relu par les tests. */
    private static ServletOutputStream sink(ByteArrayOutputStream target) {
        return new ServletOutputStream() {
            @Override
            public void write(int b) {
                target.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
                // aucune écriture asynchrone dans ces tests
            }
        };
    }

    /**
     * Thème mocké, mais {@code init()} rend un <b>vrai</b> {@code PdfWriter} attaché au document
     * reçu : le PDF est réellement généré, et les autres appels restent vérifiables.
     */
    private void themeReel() throws Exception {
        // Le thème résolu est fourni par le service de configuration : on rend
        // la charte par défaut, ce qui reproduit exactement l'ancien rendu.
        lenient().when(documentTheme.styleFor(anyString()))
                .thenReturn(com.school.pdf.DocumentStyle.defaults());
        when(documentTheme.init(any(), anyString(), any(Document.class), any()))
                .thenAnswer(inv -> PdfWriter.getInstance(inv.getArgument(2), pdfBytes));
        lenient().when(documentTheme.academicYear()).thenReturn("2025-2026");
        lenient().when(documentTheme.generatedOn(any()))
                .thenReturn(new Paragraph("Genere le 01/01/2026"));
        lenient().when(documentTheme.dataTable(any(), any(), any(), anyBoolean(), any()))
                .thenReturn(new PdfPTable(2));
        lenient().when(documentTheme.totalRow(any(), any(), any(), any()))
                .thenReturn(new PdfPTable(2));
    }

    private Workbook excelLu() throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(excelBytes.toByteArray()));
    }

    // ==================================================================
    // Excel : contenu réellement écrit
    // ==================================================================

    @Nested
    @DisplayName("exportExcel")
    class ExportExcel {

        @Test
        void ecritLaLigneDEnTetePuisLesLignes() throws Exception {
            String[] headers = {"Matricule", "Nom"};
            List<String[]> rows = List.of(
                    new String[]{"ENS-2026-0001", "Kamdem"},
                    new String[]{"ENS-2026-0002", "Siby"});

            service.exportExcel(response, headers, rows, "liste-enseignants");

            try (Workbook wb = excelLu()) {
                Sheet sheet = wb.getSheetAt(0);
                assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Matricule");
                assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Nom");
                assertThat(sheet.getLastRowNum()).as("1 en-tete + 2 lignes").isEqualTo(2);
                assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("ENS-2026-0002");
                assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("Siby");
            }
        }

        @Test
        void remplaceUneCelluleNulleParUneChaineVide() throws Exception {
            service.exportExcel(response, new String[]{"A", "B"},
                    List.<String[]>of(new String[]{"present", null}), "t");

            try (Workbook wb = excelLu()) {
                Cell cell = wb.getSheetAt(0).getRow(1).getCell(1);
                assertThat(cell)
                        .as("une cellule nulle doit exister, sinon les colonnes se decalent")
                        .isNotNull();
                assertThat(cell.getCellType())
                        .as("POI accepte 'null' en ecrivant une cellule VIDE (BLANK) : le garde doit "
                                + "produire une vraie chaine vide (STRING), pas une cellule absente")
                        .isEqualTo(CellType.STRING);
                assertThat(cell.getStringCellValue()).isEmpty();
            }
        }

        @Test
        void ecritUneFeuilleMemeSansAucuneLigne() throws Exception {
            service.exportExcel(response, new String[]{"A", "B"}, List.of(), "vide");

            try (Workbook wb = excelLu()) {
                assertThat(wb.getNumberOfSheets()).isEqualTo(1);
                assertThat(wb.getSheetAt(0).getLastRowNum()).as("l'en-tete reste seul").isZero();
            }
        }

        @Test
        void assainitLeNomDuFichier() throws Exception {
            service.exportExcel(response, new String[]{"A"}, List.of(), "Bilan Financier");

            verify(response).setHeader("Content-Disposition",
                    "attachment; filename=\"bilan-financier.xlsx\"");
        }

        @Test
        void assainitLeNomDeLOnglet() throws Exception {
            service.exportExcel(response, new String[]{"A"}, List.of(), "Depenses 2026");

            try (Workbook wb = excelLu()) {
                assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("depenses_2026");
            }
        }

        @Test
        void neutraliseUneTentativeDeTraverseeDeCheminDansLeNomDeFichier() throws Exception {
            service.exportExcel(response, new String[]{"A"}, List.of(), "../../etc/passwd");

            ArgumentCaptor<String> header = ArgumentCaptor.forClass(String.class);
            verify(response).setHeader(eq("Content-Disposition"), header.capture());

            assertThat(header.getValue())
                    .as("un en-tete Content-Disposition n'est pas un chemin : aucun separateur, aucun '..'")
                    .doesNotContain("/")
                    .doesNotContain("\\")
                    .doesNotContain("..");
        }

        @Test
        void translittereUnAccentEnSeparateur() throws Exception {
            service.exportExcel(response, new String[]{"A"}, List.of(), "Année");

            // "Année" -> "année" -> chaque caractere hors [a-z0-9-_] devient "-" -> "ann-e"
            verify(response).setHeader("Content-Disposition",
                    "attachment; filename=\"ann-e.xlsx\"");
            try (Workbook wb = excelLu()) {
                assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("ann_e");
            }
        }

        @Test
        void unTitreNonVideNeProduitJamaisUnNomDOngletVide() throws Exception {
            // "!!!" n'a aucune lettre : assaini en "---", il reste acceptable pour Excel.
            service.exportExcel(response, new String[]{"A"}, List.of(), "!!!");

            verify(response).setHeader("Content-Disposition",
                    "attachment; filename=\"---.xlsx\"");
            try (Workbook wb = excelLu()) {
                assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("___");
            }
        }

        @Test
        void tronqueLeNomDeLOngletA30Caracteres() throws Exception {
            String titre = "rapport-tres-long-de-plus-de-trente-caracteres-pour-un-onglet";

            service.exportExcel(response, new String[]{"A"}, List.of(), titre);

            try (Workbook wb = excelLu()) {
                assertThat(wb.getSheetAt(0).getSheetName())
                        .as("Excel plafonne a 31 caracteres : le service doit tronquer avant")
                        .hasSize(30)
                        .matches("[A-Za-z0-9_]+");
            }
        }
    }

    // ==================================================================
    // PDF tabulaire : decisions du service, verifiables sur le theme
    // ==================================================================

    @Nested
    @DisplayName("exportTablePdf")
    class ExportTablePdf {

        @BeforeEach
        void theme() throws Exception {
            themeReel();
        }

        @Test
        void composeLeSousTitreQuandLeLabelEtLaValeurSontFournis() throws Exception {
            service.exportTablePdf(response, "Rapport de presences", new String[]{"A"}, List.of(),
                    "Periode", "Janvier 2026", null);

            verify(documentTheme).addTitle(any(Document.class), eq("Rapport de presences"),
                    eq("Periode : Janvier 2026"), any(), any());
        }

        @Test
        void ometLeSousTitreQuandLeLabelManque() throws Exception {
            service.exportTablePdf(response, "Rapport", new String[]{"A"}, List.of(),
                    null, "Janvier 2026", null);

            verify(documentTheme).addTitle(any(Document.class), eq("Rapport"), isNull(), any(), any());
        }

        @Test
        void ometLeSousTitreQuandLaValeurManque() throws Exception {
            service.exportTablePdf(response, "Rapport", new String[]{"A"}, List.of(),
                    "Periode", null, null);

            verify(documentTheme).addTitle(any(Document.class), eq("Rapport"), isNull(), any(), any());
        }

        @Test
        void nAjouteAucuneLigneDeTotalQuandElleNEstPasFournie() throws Exception {
            service.exportTablePdf(response, "Rapport", new String[]{"A"}, List.of(),
                    null, null, null);

            verify(documentTheme, never()).totalRow(any(), any(), any(), any());
        }

        @Test
        void transmetLaLigneDeTotalQuandElleExiste() throws Exception {
            service.exportTablePdf(response, "Bilan financier", new String[]{"A"}, List.of(),
                    null, null, new String[]{"TOTAL", "1 250 000 FCFA"});

            verify(documentTheme).totalRow(eq("TOTAL"), eq("1 250 000 FCFA"),
                    eq(com.school.pdf.DocumentStyle.defaults().primary), any());
        }

        @Test
        void activeLeZebrageEtTransmetLesDonneesTellesQuelles() throws Exception {
            String[] headers = {"A", "B"};
            List<String[]> rows = List.<String[]>of(new String[]{"1", "2"});

            service.exportTablePdf(response, "Rapport", headers, rows, null, null, null);

            verify(documentTheme).dataTable(same(headers), same(rows), isNull(), eq(true), any());
        }

        @Test
        void assainitLeNomDuFichierPdf() throws Exception {
            service.exportTablePdf(response, "Etat Mensuel", new String[]{"A"}, List.of(),
                    null, null, null);

            verify(documentTheme).init(same(response), eq("etat-mensuel"), any(Document.class), any());
        }

        @Test
        void produitUnPdfNonVide() throws Exception {
            service.exportTablePdf(response, "Rapport", new String[]{"A"},
                    List.<String[]>of(new String[]{"valeur"}), null, null, null);

            assertThat(pdfBytes.toByteArray())
                    .as("le document doit avoir ete reellement ecrit, pas seulement decore")
                    .isNotEmpty();
        }
    }

    @Nested
    @DisplayName("exportStudentsPdf")
    class ExportStudentsPdf {

        @BeforeEach
        void theme() throws Exception {
            themeReel();
        }

        @Test
        void transmetLeTitreEtLAnneeScolaireDeLaSourceDeVerite() throws Exception {
            service.exportStudentsPdf(response, List.<String[]>of(new String[]{"1"}), new String[]{"A"},
                    "Liste des eleves");

            verify(documentTheme).addTitle(any(Document.class), eq("Liste des eleves"),
                    eq("Année scolaire 2025-2026"), any(), any());
        }

        @Test
        void assainitLeNomDuFichier() throws Exception {
            service.exportStudentsPdf(response, List.of(), new String[]{"A"}, "Liste des eleves");

            verify(documentTheme).init(same(response), eq("liste-des-eleves"), any(Document.class), any());
        }

        @Test
        void produitUnPdfNonVide() throws Exception {
            service.exportStudentsPdf(response, List.<String[]>of(new String[]{"1"}), new String[]{"A"}, "Liste");

            assertThat(pdfBytes.toByteArray()).isNotEmpty();
        }
    }
}
