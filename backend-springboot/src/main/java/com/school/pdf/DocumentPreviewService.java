package com.school.pdf;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.school.dto.response.BulletinLineResponse;
import com.school.dto.response.BulletinResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Aperçu des modèles de documents.
 *
 * <p>Le rendu passe par <b>les mêmes composants</b> que les vrais documents
 * ({@link SchoolDocumentTheme}) et par le thème fourni par l'écran (non
 * enregistré), donc ce que l'administrateur voit est ce qui sortira à
 * l'impression.</p>
 *
 * <p><b>Aucune donnée réelle</b> : les valeurs de démonstration sont
 * constantes, et le pied de page porte la mention « DONNÉES DE DÉMONSTRATION ».
 * Rien n'est lu en base ni écrit.</p>
 */
@Service
@RequiredArgsConstructor
public class DocumentPreviewService {

    private static final String DEMO_BANNER =
            "APERÇU — DONNÉES DE DÉMONSTRATION, SANS VALEUR OFFICIELLE";

    private final SchoolDocumentTheme theme;

    /**
     * Produit l'aperçu d'un type de document avec la configuration fournie.
     *
     * @param documentType code du catalogue ; {@code null} ou inconnu → aperçu complet
     * @param style        configuration à simuler (jamais enregistrée)
     */
    public void preview(HttpServletResponse response, String documentType, DocumentStyle style)
            throws IOException {
        DocumentStyle s = style != null ? style : DocumentStyle.defaults();
        switch (documentType == null ? "" : documentType.toUpperCase(java.util.Locale.ROOT)) {
            case "BULLETIN", "CLASS_BULLETINS" -> bulletinPreview(response, s);
            case "SCHOOL_CERTIFICATE" -> certificatePreview(response, s);
            case "PAYMENT_RECEIPT", "TEACHER_PAYMENT_RECEIPT" -> receiptPreview(response, s);
            case "STUDENT_CARD" -> studentCardPreview(response, s);
            case "ATTENDANCE_CERTIFICATE" -> attendanceCertificatePreview(response, s);
            default -> showcasePreview(response, s);
        }
    }

    // ------------------------------------------------------------------
    // Aperçu complet : en-tête, titre, sections, tableau, totaux, signatures
    // ------------------------------------------------------------------

    private void showcasePreview(HttpServletResponse response, DocumentStyle s) throws IOException {
        Document document = new Document(s.pageSize(), s.marginLeft, s.marginRight,
                s.marginTop, s.marginBottom);
        PdfWriter writer = theme.init(response, "apercu-modele", document, s);
        document.open();
        try {
            theme.addHeader(document, s);
            theme.addTitle(document, "APERÇU DU MODÈLE",
                    "Exemple de rendu — " + s.template.label(), s);

            Paragraph banner = new Paragraph(DEMO_BANNER, s.bold(8f));
            banner.setAlignment(Element.ALIGN_CENTER);
            banner.setSpacingAfter(6);
            document.add(banner);

            theme.sectionTitle(document, "BLOC D'INFORMATIONS", s);
            document.add(theme.infoTable(new String[][]{
                    {"Élève", "Aminata TRAORÉ"},
                    {"Matricule", "SE-2026-0042"},
                    {"Classe", "6ème A"},
                    {"Année scolaire", "2025-2026"},
            }, s));
            document.add(Chunk.NEWLINE);

            theme.sectionTitle(document, "TABLEAU DE DONNÉES", s);
            PdfPTable table = theme.dataTable(
                    new String[]{"Désignation", "Quantité", "Montant"},
                    java.util.List.of(
                            new String[]{"Frais de scolarité — 1er trimestre", "1", "45 000 FCFA"},
                            new String[]{"Frais de scolarité — 2e trimestre", "1", "45 000 FCFA"},
                            new String[]{"Tenue sportive", "1", "7 500 FCFA"},
                            new String[]{"Fournitures", "2", "6 000 FCFA"}),
                    new float[]{3f, 1f, 1.6f}, true, s);
            table.setHeaderRows(1);
            document.add(table);
            document.add(Chunk.NEWLINE);

            document.add(theme.totalRow("TOTAL", "103 500 FCFA", s.primary, s));
            document.add(Chunk.NEWLINE);
            document.add(theme.signatureBlock(s, "Le Comptable", "Le Directeur"));
            document.add(Chunk.NEWLINE);
            document.add(theme.generatedOn(s));
        } finally {
            document.close();
        }
    }

    // ------------------------------------------------------------------
    // Bulletin : structure réelle (7 colonnes, synthèse, moyenne, rang)
    // ------------------------------------------------------------------

    public void bulletinPreview(HttpServletResponse response, DocumentStyle s) throws IOException {
        BulletinResponse demo = BulletinResponse.builder()
                .studentName("Aminata TRAORÉ")
                .matricule("SE-2026-0042")
                .className("6ème A")
                .term(com.school.enums.Term.T1)
                .academicYear("2025-2026")
                .classAverage(new java.math.BigDecimal("12.40"))
                .classMax(new java.math.BigDecimal("17.85"))
                .classMin(new java.math.BigDecimal("6.10"))
                .average(new java.math.BigDecimal("14.32"))
                .rank(3)
                .observation("Bon trimestre")
                .totalCoefficient(18)
                .totalPoints(new java.math.BigDecimal("257.75"))
                .lines(java.util.List.of(
                        line("Français", "14.5", "13", 4, "55.0", "13.75", "Bien"),
                        line("Mathématiques", "16", "15.5", 4, "62.0", "15.75", "Très bien"),
                        line("Sciences d'observation", "13", "12.5", 3, "38.25", "12.75", "Assez bien"),
                        line("Histoire-Géographie", "11", "10.5", 2, "21.5", "10.75", "Passable"),
                        line("Anglais", "15", "14", 3, "42.0", "14.5", "Bien"),
                        line("Éducation civique et morale", "12", "12", 2, "24.0", "12.0", "Assez bien")))
                .build();
        renderBulletin(response, demo, s);
    }

    private BulletinLineResponse line(String subject, String noteClasse,
                                                       String noteCompo, int coef,
                                                       String noteCoef, String moyenne,
                                                       String observation) {
        return BulletinLineResponse.builder()
                .subjectName(subject)
                .noteClasse(new java.math.BigDecimal(noteClasse))
                .noteComposition(new java.math.BigDecimal(noteCompo))
                .coefficient(coef)
                .noteCoef(new java.math.BigDecimal(noteCoef))
                .moyenne(new java.math.BigDecimal(moyenne))
                .observation(observation)
                .build();
    }

    /**
     * Bulletin complet : en-tête, tableau des matières, synthèse, moyenne +
     * rang, signatures. Reproduction de la structure réelle, avec des notes de
     * démonstration.
     */
    public void renderBulletin(HttpServletResponse response, BulletinResponse bulletin,
                               DocumentStyle s) throws IOException {
        Document document = new Document(s.pageSize(), s.marginLeft, s.marginRight,
                s.marginTop, s.marginBottom);
        PdfWriter writer = theme.init(response, "apercu-bulletin", document, s);
        document.open();
        try {
            theme.addHeader(document, s);
            theme.addTitle(document, "BULLETIN DE NOTES",
                    bulletin.getStudentName() + "  •  " + bulletin.getClassName()
                            + "  •  " + bulletin.getAcademicYear(), s);

            Paragraph banner = new Paragraph(DEMO_BANNER, s.bold(8f));
            banner.setAlignment(Element.ALIGN_CENTER);
            document.add(banner);
            document.add(Chunk.NEWLINE);

            document.add(theme.infoTable(new String[][]{
                    {"Élève", bulletin.getStudentName()},
                    {"Matricule", bulletin.getMatricule()},
                    {"Classe", bulletin.getClassName()},
                    {"Trimestre", bulletin.getTerm().name()},
                    {"Année scolaire", bulletin.getAcademicYear()},
            }, s));
            document.add(Chunk.NEWLINE);
            theme.sectionTitle(document, "NOTES PAR MATIÈRE", s);

            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3.0f, 1.5f, 1.5f, 0.7f, 1.9f, 1.8f, 1.7f});
            float headerSize = Math.min(7.5f, Math.max(6.5f, s.tableSize - 1.5f));
            for (String header : new String[]{"Matières", "Note/classe", "Note/compo", "Coef",
                    "Note/compo coef", "Moyenne générale", "Observation"}) {
                var cell = new com.lowagie.text.pdf.PdfPCell(
                        new Paragraph(header, s.whiteBold(headerSize)));
                cell.setBackgroundColor(s.tableHeader);
                cell.setBorder(0);
                cell.setPadding(5);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                table.addCell(cell);
            }
            boolean stripe = false;
            for (BulletinLineResponse l : bulletin.getLines()) {
                String[] values = {
                        l.getSubjectName(), l.getNoteClasse().toString(),
                        l.getNoteComposition().toString(), String.valueOf(l.getCoefficient()),
                        l.getNoteCoef().toString(), l.getMoyenne().toString(), l.getObservation()};
                for (String v : values) {
                    var cell = new com.lowagie.text.pdf.PdfPCell(
                            new Paragraph(v, s.body(Math.max(7.5f, s.tableSize - 0.5f))));
                    cell.setPadding(5);
                    cell.setBorder(0);
                    cell.setBorderWidthBottom(0.6f);
                    cell.setBorderColor(s.border);
                    if (stripe && s.zebraStripes) {
                        cell.setBackgroundColor(s.sectionBackground);
                    }
                    table.addCell(cell);
                }
                stripe = !stripe;
            }
            table.setHeaderRows(1);
            document.add(table);
            document.add(Chunk.NEWLINE);

            theme.sectionTitle(document, "SYNTHÈSE", s);
            document.add(theme.infoTable(new String[][]{
                    {"Moyenne de la classe", bulletin.getClassAverage().toString()},
                    {"Maximum", bulletin.getClassMax().toString()},
                    {"Minimum", bulletin.getClassMin().toString()},
                    {"Appréciation", bulletin.getObservation()},
                    {"Décision", "ADMIS"},
            }, s));
            document.add(Chunk.NEWLINE);

            PdfPTable highlight = new PdfPTable(2);
            highlight.setWidthPercentage(80);
            highlight.setHorizontalAlignment(Element.ALIGN_CENTER);
            var avgBox = new com.lowagie.text.pdf.PdfPCell();
            avgBox.setBackgroundColor(s.primary);
            avgBox.setBorder(0);
            avgBox.setPadding(10);
            avgBox.setHorizontalAlignment(Element.ALIGN_CENTER);
            avgBox.addElement(new Paragraph("MOYENNE GÉNÉRALE",
                    DocumentStyle.fontOf(s.fontFamily, 8f, com.lowagie.text.Font.NORMAL, s.headerText)));
            avgBox.addElement(new Paragraph(bulletin.getAverage() + "/20", s.whiteBold(18f)));
            var rankBox = new com.lowagie.text.pdf.PdfPCell();
            rankBox.setBackgroundColor(s.sectionBackground);
            rankBox.setBorder(0);
            rankBox.setPadding(10);
            rankBox.setHorizontalAlignment(Element.ALIGN_CENTER);
            rankBox.addElement(new Paragraph("RANG", s.muted(8f)));
            rankBox.addElement(new Paragraph(bulletin.getRank() + "ᵉ", s.heading(18f)));
            highlight.addCell(avgBox);
            highlight.addCell(rankBox);
            document.add(highlight);
            document.add(Chunk.NEWLINE);
            document.add(theme.signatureBlock(s, "Le Responsable Pédagogique", "Le Directeur"));
        } finally {
            document.close();
        }
    }

    // ------------------------------------------------------------------
    // Certificat : texte officiel conservé, mise en page thémée
    // ------------------------------------------------------------------

    public void certificatePreview(HttpServletResponse response, DocumentStyle s) throws IOException {
        Document document = new Document(s.pageSize(), s.marginLeft, s.marginRight,
                s.marginTop, s.marginBottom);
        PdfWriter writer = theme.init(response, "apercu-certificat", document, s);
        document.open();
        try {
            theme.addHeader(document, s);
            theme.addTitle(document, "CERTIFICAT DE SCOLARITÉ",
                    "Année scolaire 2025-2026", s);

            Paragraph banner = new Paragraph(DEMO_BANNER, s.bold(8f));
            banner.setAlignment(Element.ALIGN_CENTER);
            document.add(banner);
            document.add(Chunk.NEWLINE);

            Paragraph intro = new Paragraph("Je soussigné, Directeur de "
                    + theme.schoolName()
                    + ", certifie que l'élève ci-dessous est régulièrement inscrit "
                    + "dans notre établissement :", s.body());
            intro.setLeading(s.bodySize * 1.45f);
            document.add(intro);
            document.add(Chunk.NEWLINE);

            document.add(theme.infoTable(new String[][]{
                    {"Nom", "TRAORÉ"},
                    {"Prénom(s)", "Aminata"},
                    {"Matricule", "SE-2026-0042"},
                    {"Date de naissance", "12/04/2013"},
                    {"Lieu de naissance", "Bamako"},
                    {"Classe", "6ème A"},
                    {"Année scolaire", "2025-2026"},
                    {"Parent / Tuteur", "Ibrahim TRAORÉ"},
            }, s));
            document.add(Chunk.NEWLINE);

            Paragraph conclusion = new Paragraph(
                    "En foi de quoi, le présent certificat est délivré pour servir et valoir "
                            + "ce que de droit.", s.italic(s.bodySize - 1));
            conclusion.setSpacingBefore(s.sectionSpacing);
            document.add(conclusion);
            document.add(Chunk.NEWLINE);

            Paragraph done = new Paragraph("Fait le "
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    s.body());
            done.setAlignment(Element.ALIGN_RIGHT);
            document.add(done);
            document.add(Chunk.NEWLINE);
            document.add(theme.signatureBlock(s, "Le Directeur"));
        } finally {
            document.close();
        }
    }

    // ------------------------------------------------------------------
    // Reçu : montants et mentions, structure inchangée
    // ------------------------------------------------------------------

    public void receiptPreview(HttpServletResponse response, DocumentStyle s) throws IOException {
        Document document = new Document(s.pageSize(), s.marginLeft, s.marginRight,
                s.marginTop, s.marginBottom);
        PdfWriter writer = theme.init(response, "apercu-recu", document, s);
        document.open();
        try {
            theme.addHeader(document, s);
            theme.addTitle(document, "REÇU DE PAIEMENT",
                    "N° SE-REC-2026-0187  •  "
                            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), s);

            Paragraph banner = new Paragraph(DEMO_BANNER, s.bold(8f));
            banner.setAlignment(Element.ALIGN_CENTER);
            document.add(banner);
            document.add(Chunk.NEWLINE);

            document.add(theme.infoTable(new String[][]{
                    {"Élève", "Aminata TRAORÉ"},
                    {"Matricule", "SE-2026-0042"},
                    {"Classe", "6ème A"},
                    {"Type de frais", "Scolarité — 1er trimestre"},
                    {"Mode de paiement", "Espèces"},
            }, s));
            document.add(Chunk.NEWLINE);
            document.add(theme.totalRow("MONTANT PAYÉ", "45 000 FCFA", s.primary, s));
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("Solde restant : 0 FCFA", s.bold(s.tableSize)));
            document.add(Chunk.NEWLINE);
            document.add(theme.signatureBlock(s, "Le Caissier", "Le Directeur"));
        } finally {
            document.close();
        }
    }

    // ------------------------------------------------------------------
    // Documents à mise en page figée
    // ------------------------------------------------------------------

    /**
     * Carte scolaire (format ID-1, 85,6 × 54 mm).
     *
     * <p>La carte réelle est dessinée au {@code PdfContentByte} dans
     * {@code ReportService} : sa géométrie (deux faces, marges de sécurité)
     * n'est pas pilotée par le thème. L'aperçu respecte donc le même format et
     * montre seulement ce que le thème contrôle — couleurs, logo, typographie —
     * au lieu de faire croire à un format A4 modifiable.</p>
     */
    public void studentCardPreview(HttpServletResponse response, DocumentStyle s) throws IOException {
        com.lowagie.text.Rectangle carte = new com.lowagie.text.Rectangle(243f, 153f);
        Document document = new Document(carte, 12, 12, 12, 12);
        PdfWriter writer = theme.init(response, "apercu-carte", document, s);
        document.open();
        try {
            PdfPTable bandeau = new PdfPTable(1);
            bandeau.setWidthPercentage(100);
            var cellBand = new com.lowagie.text.pdf.PdfPCell();
            cellBand.setBackgroundColor(s.primary);
            cellBand.setBorder(0);
            cellBand.setPadding(6);

            Paragraph ecole = new Paragraph(theme.schoolName(), s.whiteBold(9f));
            cellBand.addElement(ecole);
            Paragraph type = new Paragraph("CARTE SCOLAIRE " + theme.academicYear(),
                    s.whiteBold(6.5f));
            cellBand.addElement(type);
            bandeau.addCell(cellBand);
            document.add(bandeau);

            document.add(new Paragraph(" ", s.body(4f)));

            document.add(theme.infoTable(new String[][]{
                    {"Nom", "TRAORÉ"},
                    {"Prénom", "Aminata"},
                    {"Matricule", "SE-2026-0042"},
                    {"Classe", "6ème A"},
                    {"Né(e) le", "12/04/2013"},
            }, s));

            Paragraph mention = new Paragraph("APERÇU — SANS VALEUR OFFICIELLE", s.muted(6f));
            mention.setAlignment(Element.ALIGN_CENTER);
            mention.setSpacingBefore(4);
            document.add(mention);
        } finally {
            document.close();
        }
    }

    /**
     * Certificat de fréquentation (A5).
     *
     * <p>Comme la carte, le document réel est dessiné sur un canvas A5 fixe :
     * l'aperçu conserve ce format pour que l'administrateur voie la vraie
     * densité de texte, sans jamais proposer « paysage » sur ce type.</p>
     */
    public void attendanceCertificatePreview(HttpServletResponse response, DocumentStyle s)
            throws IOException {
        Document document = new Document(com.lowagie.text.PageSize.A5,
                Math.min(28f, s.marginLeft), Math.min(28f, s.marginRight),
                24, 24);
        PdfWriter writer = theme.init(response, "apercu-frequentation", document, s);
        document.open();
        try {
            theme.addHeader(document, s);
            theme.addTitle(document, "CERTIFICAT DE FRÉQUENTATION",
                    "Année scolaire 2025-2026", s);

            Paragraph banner = new Paragraph(DEMO_BANNER, s.bold(7f));
            banner.setAlignment(Element.ALIGN_CENTER);
            document.add(banner);
            document.add(Chunk.NEWLINE);

            Paragraph intro = new Paragraph("Je soussigné, Directeur de "
                    + theme.schoolName()
                    + ", atteste que l'élève désigné ci-dessous fréquente régulièrement "
                    + "notre établissement :", s.body(Math.max(8f, s.bodySize - 1)));
            intro.setLeading((s.bodySize - 1) * 1.45f);
            document.add(intro);
            document.add(Chunk.NEWLINE);

            document.add(theme.infoTable(new String[][]{
                    {"Élève", "Aminata TRAORÉ"},
                    {"Matricule", "SE-2026-0042"},
                    {"Classe", "6ème A"},
                    {"Période", "1er trimestre 2025-2026"},
                    {"Absences", "2 justifiées"},
            }, s));
            document.add(Chunk.NEWLINE);

            document.add(theme.signatureBlock(s, "Le Directeur"));
        } finally {
            document.close();
        }
    }
}
