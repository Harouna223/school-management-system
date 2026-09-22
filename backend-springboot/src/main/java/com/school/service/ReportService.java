package com.school.service;

import com.school.config.AppProperties;
import com.school.pdf.SchoolDocumentTheme;
import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Génération de documents : bulletins PDF, reçus PDF, carte scolaire, certificats, exports Excel.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReportService {

    private final AppProperties appProperties;
    private final SettingService settingService;
    private final QrCodeService qrCodeService;
    private final SchoolDocumentTheme documentTheme;

    // ---------- Palette des documents officiels (modèle de référence) ----------

    private static final Color NAVY = new Color(15, 42, 78);          // #0f2a4e
    private static final Color DARK_TEXT = new Color(40, 48, 58);     // #28303a
    private static final Color GOLD = new Color(179, 144, 88);        // #b39058
    private static final Color GRAY_TEXT = new Color(122, 128, 138);  // #7a808a
    private static final Color FOOTER_GRAY = new Color(208, 213, 221);// #d0d5dd
    private static final Color FLAG_GREEN = new Color(20, 181, 58);
    private static final Color FLAG_YELLOW = new Color(252, 209, 22);
    private static final Color FLAG_RED = new Color(206, 17, 38);
    private static final Color FLAG_BORDER = new Color(122, 128, 138);

    // ---------- Polices (Georgia si disponible, sinon Times) ----------

    private static boolean georgiaAvailable = false;

    static {
        String[] candidates = {
                "C:/Windows/Fonts/georgia.ttf",
                "C:/Windows/Fonts/georgiab.ttf",
                "C:/Windows/Fonts/georgiai.ttf",
                "C:/Windows/Fonts/georgiaz.ttf",
                "/usr/share/fonts/truetype/msttcorefonts/Georgia.ttf",
                "/usr/share/fonts/truetype/msttcorefonts/Georgia_Bold.ttf"
        };
        for (String path : candidates) {
            try {
                if (Files.exists(Paths.get(path))) {
                    FontFactory.register(path);
                    georgiaAvailable = true;
                }
            } catch (Exception ignored) {
                // police indisponible : repli sur Times
            }
        }
    }

    private Font serifBold(float size, Color color) {
        return serif(size, Font.BOLD, color, FontFactory.TIMES_BOLD);
    }

    private Font serif(float size, int style, Color color, String fallbackFamily) {
        if (georgiaAvailable) {
            try {
                return FontFactory.getFont("Georgia", BaseFont.CP1252, size, style, color);
            } catch (Exception ignored) {
                // repli ci-dessous
            }
        }
        return FontFactory.getFont(fallbackFamily, size, style, color);
    }

    private Font sansBold(float size, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, size, color);
    }

    private Font sans(float size, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, color);
    }

    // ---------- PDF ----------

    public void exportStudentsPdf(HttpServletResponse response,
                                  List<String[]> rows, String[] headers, String title)
            throws IOException {
        Document document = new Document(PageSize.A4.rotate(), 32, 32, 32, 70);
        PdfWriter writer = documentTheme.init(response, sanitize(title), document);
        document.open();
        try {
            documentTheme.addHeader(document);
            documentTheme.addTitle(document, title, "Année scolaire " + documentTheme.academicYear());
            PdfPTable table = documentTheme.dataTable(headers, rows, null, true);
            table.setHeaderRows(1);
            document.add(table);
            document.add(Chunk.NEWLINE);
            document.add(documentTheme.generatedOn());
        } finally {
            safeClose(document);
        }
    }

    /**
     * Reçu de paiement PDF (A5) avec logo, coordonnées, montant payé et solde restant.
     */
    public void paymentReceiptPdf(HttpServletResponse response, String receiptNo,
                                  String studentName, String matricule, String feeType,
                                  String amount, String method, String date, String note,
                                  String amountInWords, String remainingAmount)
            throws IOException {
        Document document = new Document(PageSize.A5, 28, 28, 28, 55);
        PdfWriter writer = documentTheme.init(response, "recu-" + receiptNo, document);
        document.open();
        documentTheme.addHeader(document);

        documentTheme.addTitle(document, "REÇU DE PAIEMENT",
                "N° " + receiptNo + "  •  " + date);

        try {

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        addRow(table, "Élève", studentName);
        addRow(table, "Matricule", matricule);
        addRow(table, "Motif / Frais", feeType);
        addRow(table, "Mode de paiement", method);
        if (remainingAmount != null && !remainingAmount.isBlank()) {
            addRow(table, "Restant à payer", remainingAmount);
        }
        if (note != null && !note.isBlank()) {
            addRow(table, "Note", note);
        }
        document.add(table);
        document.add(Chunk.NEWLINE);

        // Montant payé mis en évidence
        PdfPTable amountRow = new PdfPTable(2);
        amountRow.setWidthPercentage(100);
        PdfPCell amountLabel = new PdfPCell(new Phrase("MONTANT PAYÉ", SchoolDocumentTheme.bold(11f)));
        amountLabel.setBorder(0);
        amountLabel.setPadding(6);
        amountLabel.setBackgroundColor(SchoolDocumentTheme.HEADER_BG);
        PdfPCell amountVal = new PdfPCell(new Phrase(amount, SchoolDocumentTheme.whiteBold(14f)));
        amountVal.setBorder(0);
        amountVal.setPadding(6);
        amountVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        amountVal.setBackgroundColor(SchoolDocumentTheme.PRIMARY);
        amountRow.addCell(amountLabel);
        amountRow.addCell(amountVal);
        document.add(amountRow);

        if (amountInWords != null && !amountInWords.isBlank()) {
            document.add(Chunk.NEWLINE);
            Paragraph inWords = new Paragraph("Arrêté le présent reçu à la somme de : " + amountInWords,
                    SchoolDocumentTheme.muted(9f));
            inWords.setSpacingBefore(6);
            document.add(inWords);
        }

        document.add(Chunk.NEWLINE);
        document.add(documentTheme.signatureBlock("Caissier", "Direction"));

        document.add(Chunk.NEWLINE);
        document.add(new Paragraph(settingService.value(SettingService.RECEIPT_FOOTER, "Merci de votre confiance."),
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, SchoolDocumentTheme.TEXT_MUTED)));
        } finally {
            safeClose(document);
        }
    }

    /**
     * Reçu de paiement du salaire d'un enseignant (PDF A5) :
     * en-tête école, mois, heures × tarif = montant dû, montant payé, restant,
     * mode de paiement, zone double signature.
     */
    public void teacherPaymentReceiptPdf(HttpServletResponse response, String receiptNo,
                                         String teacherName, String monthLabel, String academicYearLabel,
                                         String totalHours, String hourlyRate, String totalAmount,
                                         String amount, String method, String date,
                                         String reference, String recordedBy,
                                         String remainingAmount, String amountInWords)
            throws IOException {
        Document document = new Document(PageSize.A5, 28, 28, 28, 55);
        PdfWriter writer = documentTheme.init(response, "recu-enseignant-" + receiptNo, document);
        document.open();
        documentTheme.addHeader(document);

        documentTheme.addTitle(document, "REÇU DE PAIEMENT ENSEIGNANT",
                "N° " + receiptNo + "  •  " + date);

        try {

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        addRow(table, "Enseignant", teacherName);
        addRow(table, "Mois concerné", monthLabel);
        if (academicYearLabel != null && !academicYearLabel.isBlank()) {
            addRow(table, "Année scolaire", academicYearLabel);
        }
        addRow(table, "Nombre d'heures", totalHours + " h");
        addRow(table, "Tarif horaire", hourlyRate + " FCFA");
        addRow(table, "Calcul", totalHours + " h × " + hourlyRate + " = " + totalAmount + " FCFA");
        addRow(table, "Montant total dû", totalAmount + " FCFA");
        addRow(table, "Montant payé", amount + " FCFA");
        addRow(table, "Reste à payer", remainingAmount + " FCFA");
        addRow(table, "Mode de paiement", method);
        if (reference != null && !reference.isBlank()) {
            addRow(table, "Référence", reference);
        }
        if (recordedBy != null && !recordedBy.isBlank()) {
            addRow(table, "Enregistré par", recordedBy);
        }
        document.add(table);
        document.add(Chunk.NEWLINE);

        // Montant payé mis en évidence
        PdfPTable amountRow = new PdfPTable(2);
        amountRow.setWidthPercentage(100);
        PdfPCell amountLabel = new PdfPCell(new Phrase("MONTANT PAYÉ", SchoolDocumentTheme.bold(11f)));
        amountLabel.setBorder(0);
        amountLabel.setPadding(6);
        amountLabel.setBackgroundColor(SchoolDocumentTheme.HEADER_BG);
        PdfPCell amountVal = new PdfPCell(new Phrase(amount + " FCFA", SchoolDocumentTheme.whiteBold(14f)));
        amountVal.setBorder(0);
        amountVal.setPadding(6);
        amountVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        amountVal.setBackgroundColor(SchoolDocumentTheme.PRIMARY);
        amountRow.addCell(amountLabel);
        amountRow.addCell(amountVal);
        document.add(amountRow);

        if (amountInWords != null && !amountInWords.isBlank()) {
            document.add(Chunk.NEWLINE);
            Paragraph inWords = new Paragraph("Arrêté le présent reçu à la somme de : " + amountInWords,
                    SchoolDocumentTheme.muted(9f));
            inWords.setSpacingBefore(6);
            document.add(inWords);
        }

        document.add(Chunk.NEWLINE);
        document.add(documentTheme.signatureBlock("Signature du professeur",
                "Signature / cachet de l'établissement"));

        document.add(Chunk.NEWLINE);
        document.add(new Paragraph(settingService.value(SettingService.RECEIPT_FOOTER, "Merci de votre confiance."),
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, SchoolDocumentTheme.TEXT_MUTED)));
        } finally {
            safeClose(document);
        }
    }

    /**
     * Tableau imprimable PDF avec en-tête école, lignes de données et ligne de total.
     */
    public void exportTablePdf(HttpServletResponse response, String title,
                               String[] headers, List<String[]> rows,
                               String subtitleLabel, String subtitleValue,
                               String[] totalRow) throws IOException {
        Document document = new Document(PageSize.A4.rotate(), 28, 28, 28, 70);
        PdfWriter writer = documentTheme.init(response, sanitize(title), document);
        document.open();
        try {
            documentTheme.addHeader(document);

            String subtitle = subtitleLabel != null && subtitleValue != null
                    ? subtitleLabel + " : " + subtitleValue : null;
            documentTheme.addTitle(document, title, subtitle);

            PdfPTable table = documentTheme.dataTable(headers, rows, null, true);
            table.setHeaderRows(1);
            document.add(table);

            if (totalRow != null) {
                document.add(Chunk.NEWLINE);
                document.add(documentTheme.totalRow(totalRow[0], totalRow[1], SchoolDocumentTheme.PRIMARY));
            }

            document.add(Chunk.NEWLINE);
            document.add(documentTheme.generatedOn());
        } finally {
            safeClose(document);
        }
    }

    private void addRow(PdfPTable table, String label, String value) {
        table.addCell(new Phrase(label, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        table.addCell(new Phrase(value != null ? value : "",
                FontFactory.getFont(FontFactory.HELVETICA, 10)));
    }

    /**
     * Certificat de scolarité PDF d'un élève.
     */
    public void schoolCertificatePdf(HttpServletResponse response,
                                     com.school.dto.response.StudentResponse student)
            throws IOException {
        Document document = new Document(PageSize.A4, 40, 40, 40, 70);
        PdfWriter writer = documentTheme.init(response,
                "certificat-scolarite-" + student.getMatricule(), document);
        document.open();
        try {
            String schoolName = settingService.value(SettingService.SCHOOL_NAME,
                    appProperties.getSchool().getName());

            documentTheme.addHeader(document);
            documentTheme.addTitle(document, "CERTIFICAT DE SCOLARITÉ",
                    "Année scolaire " + documentTheme.academicYear());

            Paragraph intro = new Paragraph("Je soussigné, Directeur de " + schoolName
                            + ", certifie que l'élève ci-dessous est régulièrement inscrit dans notre établissement :",
                    SchoolDocumentTheme.body(11f));
            intro.setLeading(16f);
            document.add(intro);
            document.add(Chunk.NEWLINE);

            PdfPTable info = documentTheme.infoTable(new String[][]{
                    {"Nom", student.getLastName()},
                    {"Prénom(s)", student.getFirstName()},
                    {"Matricule", student.getMatricule()},
                    {"Date de naissance", student.getBirthDate() != null
                            ? student.getBirthDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "—"},
                    {"Lieu de naissance", student.getBirthPlace() != null ? student.getBirthPlace() : "—"},
                    {"Classe", student.getClassName()},
                    {"Année scolaire", com.school.utils.CodeGenerator.currentAcademicYear()},
                    {"Parent / Tuteur", student.getParent() != null
                            ? student.getParent().getFirstName() + " " + student.getParent().getLastName() : "—"},
            });
            info.setSpacingBefore(8);
            document.add(info);
            document.add(Chunk.NEWLINE);

            Paragraph conclusion = new Paragraph("En foi de quoi, le présent certificat est délivré pour servir et valoir ce que de droit.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, SchoolDocumentTheme.TEXT_MUTED));
            conclusion.setSpacingBefore(10);
            document.add(conclusion);

            document.add(Chunk.NEWLINE);
            Paragraph faitLe = new Paragraph("Fait le " + LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), SchoolDocumentTheme.body(10f));
            faitLe.setAlignment(Element.ALIGN_RIGHT);
            document.add(faitLe);
            document.add(Chunk.NEWLINE);
            document.add(documentTheme.signatureBlock("Le Directeur"));
        } finally {
            safeClose(document);
        }
    }

    /**
     * Bulletin scolaire trimestriel PDF d'un élève (notes par matière, moyennes, décision).
     */
    public void bulletinPdf(HttpServletResponse response,
                            com.school.dto.response.BulletinResponse bulletin,
                            List<com.school.entity.Grade> grades)
            throws IOException {
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"bulletin-" + bulletin.getMatricule() + "-"
                        + bulletin.getTerm() + ".pdf\"");
        Document document = new Document(PageSize.A4, 32, 32, 32, 70);
        PdfWriter writer = documentTheme.init(response, "bulletin-" + bulletin.getMatricule()
                + "-" + bulletin.getTerm(), document);
        document.open();
        try {
            addBulletinContent(document, bulletin, grades);
        } finally {
            safeClose(document);
        }
    }

    /**
     * Tous les bulletins d'une classe dans un document PDF unique (une page par élève).
     */
    public void classBulletinsPdf(HttpServletResponse response,
                                  List<com.school.dto.response.BulletinResponse> bulletins,
                                  List<List<com.school.entity.Grade>> gradesList)
            throws IOException {
        String className = bulletins.isEmpty() ? "classe"
                : (bulletins.get(0).getClassName() != null ? bulletins.get(0).getClassName() : "classe");
        String term = bulletins.isEmpty() ? "TERM" : bulletins.get(0).getTerm().name();
        Document document = new Document(PageSize.A4, 32, 32, 32, 70);
        PdfWriter writer = documentTheme.init(response, "bulletins-" + sanitize(className)
                + "-" + term, document);
        document.open();
        try {
            for (int i = 0; i < bulletins.size(); i++) {
                if (i > 0) {
                    document.newPage();
                }
                addBulletinContent(document, bulletins.get(i), gradesList.get(i));
            }
        } finally {
            safeClose(document);
        }
    }

    private void addBulletinContent(Document document,
                                    com.school.dto.response.BulletinResponse bulletin,
                                    List<com.school.entity.Grade> grades) {
        documentTheme.addHeader(document);
        documentTheme.addTitle(document, "BULLETIN DE NOTES",
                bulletin.getStudentName() + "  •  " + bulletin.getClassName()
                        + "  •  Trimestre " + bulletin.getTerm() + "  •  " + bulletin.getAcademicYear());

        PdfPTable info = documentTheme.infoTable(new String[][]{
                {"Élève", bulletin.getStudentName()},
                {"Matricule", bulletin.getMatricule()},
                {"Classe", bulletin.getClassName()},
                {"Trimestre", bulletin.getTerm().name()},
                {"Année scolaire", bulletin.getAcademicYear()},
        });
        document.add(info);
        document.add(Chunk.NEWLINE);

        documentTheme.sectionTitle(document, "NOTES PAR MATIÈRE");
        List<String[]> rows = new java.util.ArrayList<>();
        for (com.school.entity.Grade g : grades) {
            String subject = g.getExam().getSubject() != null ? g.getExam().getSubject().getName() : "—";
            int coef = g.getExam().getCoefficient() != null ? g.getExam().getCoefficient() : 1;
            rows.add(new String[]{subject, String.valueOf(coef),
                    g.getValue() != null ? g.getValue().toString() : "—",
                    g.getAppreciation() != null ? g.getAppreciation() : ""});
        }
        PdfPTable table = documentTheme.dataTable(
                new String[]{"Matière", "Coef.", "Note /20", "Appréciation"},
                rows, new float[]{4, 1, 1.4f, 3f}, true);
        table.setHeaderRows(1);
        document.add(table);
        document.add(Chunk.NEWLINE);

        documentTheme.sectionTitle(document, "SYNTHÈSE");
        PdfPTable summary = documentTheme.infoTable(new String[][]{
                {"Moyenne de la classe", bulletin.getClassAverage() != null ? bulletin.getClassAverage().toString() : "—"},
                {"Maximum", bulletin.getClassMax() != null ? bulletin.getClassMax().toString() : "—"},
                {"Minimum", bulletin.getClassMin() != null ? bulletin.getClassMin().toString() : "—"},
                {"Mention", bulletin.getMention() != null ? bulletin.getMention() : "—"},
                {"Décision", bulletin.getDecision() != null ? bulletin.getDecision().name() : "—"},
        });
        document.add(summary);
        document.add(Chunk.NEWLINE);

        // Moyenne générale + rang mis en évidence
        PdfPTable highlight = new PdfPTable(2);
        highlight.setWidthPercentage(80);
        highlight.setHorizontalAlignment(Element.ALIGN_CENTER);
        String avg = bulletin.getAverage() != null ? bulletin.getAverage().toString() : "—";
        PdfPCell avgBox = new PdfPCell();
        avgBox.setBackgroundColor(SchoolDocumentTheme.PRIMARY);
        avgBox.setBorder(0);
        avgBox.setPadding(10);
        avgBox.setHorizontalAlignment(Element.ALIGN_CENTER);
        avgBox.addElement(new Paragraph("MOYENNE GÉNÉRALE", SchoolDocumentTheme.muted(8f)));
        avgBox.addElement(new Paragraph(avg + "/20", SchoolDocumentTheme.whiteBold(18f)));
        String rank = bulletin.getRank() != null ? String.valueOf(bulletin.getRank()) : "—";
        PdfPCell rankBox = new PdfPCell();
        rankBox.setBackgroundColor(SchoolDocumentTheme.HEADER_BG);
        rankBox.setBorder(0);
        rankBox.setPadding(10);
        rankBox.setHorizontalAlignment(Element.ALIGN_CENTER);
        rankBox.addElement(new Paragraph("RANG", SchoolDocumentTheme.muted(8f)));
        rankBox.addElement(new Paragraph(rank + "ᵉ", SchoolDocumentTheme.heading(18f)));
        highlight.addCell(avgBox);
        highlight.addCell(rankBox);
        document.add(highlight);
        document.add(Chunk.NEWLINE);

        document.add(documentTheme.signatureBlock("Le Responsable Pédagogique", "Le Directeur"));
        document.add(Chunk.NEWLINE);
        document.add(new Paragraph(settingService.value(SettingService.RECEIPT_FOOTER,
                "Merci de votre confiance."),
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, SchoolDocumentTheme.TEXT_MUTED)));
    }

    // ---------- Carte scolaire (format ID-1 / CR80) ----------

    /**
     * Carte d'identité scolaire ID-1 (85,6 × 54 mm) d'un élève, conforme au modèle malien de
     * référence : bordure noire, emblème à gauche, République du Mali au centre, drapeau à droite,
     * titre, bande tricolore VERT-JAUNE-ROUGE, établissement, champs d'identité, photo encadrée,
     * signature du Directeur. Mise en page compacte et horizontale.
     */
    public void schoolIdCardPdf(HttpServletResponse response,
                                com.school.dto.response.StudentResponse student)
            throws IOException {
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"carte-" + student.getMatricule() + ".pdf\"");
        float w = 242.65f, h = 153.07f;
        Document document = new Document(new Rectangle(w, h), 0, 0, 0, 0);
        PdfWriter writer = PdfWriter.getInstance(document, response.getOutputStream());
        document.open();
        PdfContentByte cb = writer.getDirectContent();
        try {
            String schoolName = settingService.value(SettingService.SCHOOL_NAME,
                appProperties.getSchool().getName());
        float cx = w / 2;

        // --- Bordure noire fine tout autour de la carte ---
        cb.saveState();
        cb.setLineWidth(0.9f);
        cb.setColorStroke(Color.BLACK);
        cb.rectangle(3f, 3f, w - 6f, h - 6f);
        cb.stroke();
        cb.restoreState();

        // --- Emblème / logo de l'établissement en haut à gauche ---
        Image emblem = loadLogo();
        if (emblem != null) {
            emblem.scaleAbsolute(22f, 22f);
            emblem.setAbsolutePosition(10f, h - 5f - 22f);
            document.add(emblem);
        }

        // --- Drapeau tricolore du Mali en haut à droite (VERT | JAUNE | ROUGE) ---
        float fx = 211f, fy = h - 5f - 16f, fw = 24f, fh = 16f;
        cb.saveState();
        cb.setColorFill(FLAG_GREEN);
        cb.rectangle(fx, fy, fw / 3, fh);
        cb.fill();
        cb.setColorFill(FLAG_YELLOW);
        cb.rectangle(fx + fw / 3, fy, fw / 3, fh);
        cb.fill();
        cb.setColorFill(FLAG_RED);
        cb.rectangle(fx + 2 * fw / 3, fy, fw / 3, fh);
        cb.fill();
        cb.setLineWidth(0.3f);
        cb.setColorStroke(FLAG_BORDER);
        cb.rectangle(fx, fy, fw, fh);
        cb.stroke();
        cb.restoreState();

        // --- En-tête institutionnel (centre) ---
        centeredAt(cb, "RÉPUBLIQUE DU MALI", serifBold(7.5f, NAVY), cx, h - 12f);
        centeredAt(cb, "Un Peuple – Un But – Une Foi", sans(4.2f, DARK_TEXT), cx, h - 19f);
        centeredAt(cb, "Ministère de l'Éducation Nationale", sans(4.5f, DARK_TEXT), cx, h - 25f);

        // --- Titre ---
        centeredAt(cb, "CARTE D'IDENTITÉ SCOLAIRE", serifBold(8.5f, NAVY), cx, h - 33f);

        // --- Bande tricolore horizontale VERT | JAUNE | ROUGE (sous le titre) ---
        float bandTop = h - 38f;
        float bandBottom = h - 44f;
        cb.saveState();
        cb.setColorFill(FLAG_GREEN);
        cb.rectangle(8f, bandBottom, (w - 16f) / 3, bandTop - bandBottom);
        cb.fill();
        cb.setColorFill(FLAG_YELLOW);
        cb.rectangle(8f + (w - 16f) / 3, bandBottom, (w - 16f) / 3, bandTop - bandBottom);
        cb.fill();
        cb.setColorFill(FLAG_RED);
        cb.rectangle(8f + 2 * (w - 16f) / 3, bandBottom, (w - 16f) / 3, bandTop - bandBottom);
        cb.fill();
        cb.restoreState();

        // --- Nom de l'établissement (sous la bande) ---
        centeredAt(cb, schoolName != null ? schoolName.toUpperCase() : "ÉTABLISSEMENT SCOLAIRE",
                serifBold(6.5f, DARK_TEXT), cx, h - 51f);

        // --- Cadre photo (droite) ---
        float frameX = 163f, frameY = h - 133f, frameW = 71f, frameH = 74f;
        cb.saveState();
        cb.setLineWidth(1.1f);
        cb.setColorStroke(NAVY);
        cb.rectangle(frameX, frameY, frameW, frameH);
        cb.stroke();
        cb.restoreState();

        // --- Photo de l'élève (centrée dans le cadre, avec marge intérieure) ---
        Image photo = loadPhoto(student.getPhoto());
        if (photo != null) {
            photo.scaleToFit(48f, 56f);
            float pw = photo.getScaledWidth();
            float ph = photo.getScaledHeight();
            photo.setAbsolutePosition(frameX + (frameW - pw) / 2f, h - 133f + (frameH - ph) / 2f);
            document.add(photo);
        }

        // --- Champs d'identité (gauche, compact) ---
        addCardFieldAt(cb, "Nom : ", student.getLastName(), h - 61f);
        addCardFieldAt(cb, "Prénom(s) : ", student.getFirstName(), h - 69.5f);
        addCardFieldAt(cb, "Matricule : ", student.getMatricule(), h - 78f);
        addCardFieldAt(cb, "Date de naissance : ", birthDate(student), h - 86.5f);
        addCardFieldAt(cb, "Lieu de naissance : ",
                student.getBirthPlace() != null ? student.getBirthPlace() : "—", h - 95f);
        addCardFieldAt(cb, "Sexe : ", genderLabel(student.getGender()), h - 103.5f);
        addCardFieldAt(cb, "Classe : ",
                student.getClassName() != null ? student.getClassName() : "—", h - 112f);

        // --- Signature et cachet du Directeur (bas à gauche) ---
        leftTextAt(cb, "Signature et cachet du Directeur", sansBold(6f, DARK_TEXT), 8f, h - 143f);
        } finally {
            safeClose(document);
        }
    }

    private String genderLabel(com.school.enums.Gender gender) {
        if (gender == null) {
            return "—";
        }
        return switch (gender) {
            case MALE -> "MASCULIN";
            case FEMALE -> "FÉMININ";
        };
    }

    private void addCardFieldAt(PdfContentByte cb, String label, String value, float y) {
        Phrase p = new Phrase();
        p.add(new Chunk(label, sansBold(6f, NAVY)));
        p.add(new Chunk(value != null ? value : "—", sans(6.2f, DARK_TEXT)));
        ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, p, 7f, y, 0);
    }

    // ---------- Certificat de fréquentation (A5) ----------

    /**
     * Certificat de fréquentation A5 d'un élève, fidèle au modèle de référence :
     * entête officiel (logo + coordonnées), bordures, QR code, photo, bloc signature.
     */
    public void attendanceCertificatePdf(HttpServletResponse response,
                                         com.school.dto.response.StudentResponse student)
            throws IOException {
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"certificat_" + student.getMatricule() + ".pdf\"");
        Document document = new Document(PageSize.A5, 0, 0, 0, 0);
        PdfWriter writer = PdfWriter.getInstance(document, response.getOutputStream());
        document.open();
        PdfContentByte cb = writer.getDirectContent();
        try {
            float w = PageSize.A5.getWidth();
        float h = PageSize.A5.getHeight();

        String schoolName = settingService.value(SettingService.SCHOOL_NAME,
                appProperties.getSchool().getName());
        String address = settingService.value(SettingService.SCHOOL_ADDRESS,
                appProperties.getSchool().getAddress());
        String phone = settingService.value(SettingService.SCHOOL_PHONE,
                appProperties.getSchool().getPhone());
        String email = settingService.value(SettingService.SCHOOL_EMAIL,
                appProperties.getSchool().getEmail());
        String slogan = settingService.value(SettingService.SCHOOL_SLOGAN,
                "Éducation, Excellence et Avenir");
        String website = settingService.value(SettingService.SCHOOL_WEBSITE, "");
        String matricule = student.getMatricule() != null ? student.getMatricule() : "———";
        String issued = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String city = extractCity(address);

        // --- Bordures extérieure et intérieure ---
        cb.saveState();
        cb.setLineWidth(1.4f);
        cb.setColorStroke(NAVY);
        cb.rectangle(14f, 14f, w - 28f, h - 28f);
        cb.stroke();
        cb.setLineWidth(0.5f);
        cb.setColorStroke(GOLD);
        cb.rectangle(26f, 26f, w - 52f, h - 52f);
        cb.stroke();
        cb.restoreState();

        // --- En-tête : logo à gauche, coordonnées de l'établissement à droite ---
        Image logo = loadLogo();
        if (logo != null) {
            logo.scaleAbsolute(42f, 42f);
            logo.setAbsolutePosition(80.8f, h - 100.2f);
            document.add(logo);
        }
        float headCx = 262f;
        centeredAt(cb, schoolName != null ? schoolName : "Établissement scolaire",
                serifBold(13.5f, NAVY), headCx, h - 61.0f);
        if (slogan != null && !slogan.isBlank()) {
            centeredAt(cb, slogan, sans(8.5f, GOLD), headCx, h - 75.7f);
        }
        if (address != null && !address.isBlank()) {
            centeredAt(cb, address, sans(7.5f, GRAY_TEXT), headCx, h - 87.3f);
        }
        StringBuilder contact = new StringBuilder();
        if (phone != null && !phone.isBlank()) {
            contact.append("Tél : ").append(phone);
        }
        if (email != null && !email.isBlank()) {
            if (contact.length() > 0) {
                contact.append("   |   ");
            }
            contact.append("Email : ").append(email);
        }
        if (contact.length() > 0) {
            centeredAt(cb, contact.toString(), sans(7.5f, GRAY_TEXT), headCx, h - 98.6f);
        }
        if (website != null && !website.isBlank()) {
            centeredAt(cb, website, sans(7f, GOLD), headCx, h - 109.3f);
        }

        // --- Double trait sous l'en-tête ---
        cb.saveState();
        cb.setLineWidth(0.7f);
        cb.setColorStroke(GOLD);
        cb.moveTo(46f, h - 120.7f);
        cb.lineTo(374f, h - 120.7f);
        cb.stroke();
        cb.setLineWidth(1.2f);
        cb.setColorStroke(NAVY);
        cb.moveTo(46f, h - 122.7f);
        cb.lineTo(374f, h - 122.7f);
        cb.stroke();
        cb.restoreState();

        // --- Titre et référence ---
        centeredAt(cb, "CERTIFICAT DE FRÉQUENTATION", serifBold(13f, NAVY), w / 2, h - 149.8f);
        centeredAt(cb, "N° CERT-" + matricule, sansBold(8f, NAVY), w / 2, h - 173.7f);
        centeredAt(cb, "Émis le " + issued, sans(7.5f, GRAY_TEXT), w / 2, h - 193.1f);

        // --- QR code en haut à droite ---
        Image qr = loadStudentQr(student);
        if (qr != null) {
            qr.scaleAbsolute(31.2f, 31.2f);
            qr.setAbsolutePosition(339.4f, h - 239.1f);
            document.add(qr);
        }

        // --- Corps ---
        String fullName = (student.getFirstName() != null ? student.getFirstName() : "")
                + " " + (student.getLastName() != null ? student.getLastName() : "");
        leftWrappedAt(cb, "Je soussigné, responsable de l'établissement " + schoolName
                        + ", certifie que l'élève",
                sans(11f, DARK_TEXT), 48f, h - 264.5f, 235f, 20f);
        // Le nom est placé sous le texte d'introduction (qui peut s'enrouler sur
        // plusieurs lignes), pour éviter tout chevauchement avec la zone de texte.
        leftTextAt(cb, fullName, serifBold(14f, NAVY), 48f, h - 341.5f);
        leftTextAt(cb, "Matricule : " + matricule + "     —     né(e) le " + birthDate(student),
                sans(9f, DARK_TEXT), 48f, h - 358.8f);
        leftWrappedAt(cb, "est inscrit(e) dans la classe de "
                        + (student.getClassName() != null ? student.getClassName() : "—")
                        + " pour l'année scolaire " + com.school.utils.CodeGenerator.currentAcademicYear() + ".",
                sans(9.5f, DARK_TEXT), 48f, h - 375.8f, 235f, 0f);

        // --- Cadre + photo de l'élève (droite) ---
        cb.saveState();
        cb.setLineWidth(0.9f);
        cb.setColorStroke(NAVY);
        cb.rectangle(291.7f, h - 351.7f, 81.8f, 101.3f);
        cb.stroke();
        cb.restoreState();
        Image photo = loadPhoto(student.getPhoto());
        if (photo != null) {
            photo.scaleToFit(48.8f, 60f);
            float pw = photo.getScaledWidth();
            float ph = photo.getScaledHeight();
            photo.setAbsolutePosition(308.2f + (48.8f - pw) / 2f, h - 314.3f + (60f - ph) / 2f);
            document.add(photo);
        }

        // --- Bloc signature (droite) ---
        cb.saveState();
        cb.setLineWidth(0.7f);
        cb.setColorStroke(NAVY);
        cb.rectangle(242.2f, h - 494.8f, 131.8f, 122.7f);
        cb.stroke();
        cb.restoreState();
        float signCx = 308f;
        centeredAt(cb, "Fait à " + (city.isBlank() ? "________" : city) + ", le",
                sansBold(8.5f, NAVY), signCx, h - 391.4f);
        centeredAt(cb, issued, sansBold(8.5f, NAVY), signCx, h - 404.1f);
        cb.saveState();
        cb.setLineWidth(0.6f);
        cb.setColorStroke(Color.BLACK);
        cb.moveTo(252.9f, h - 454.1f);
        cb.lineTo(363.3f, h - 454.1f);
        cb.stroke();
        cb.restoreState();
        centeredAt(cb, "Signature et cachet de", sans(8f, GRAY_TEXT), signCx, h - 468.9f);
        centeredAt(cb, "l'établissement", sans(8f, GRAY_TEXT), signCx, h - 480.9f);

        // --- Pied de page ---
        cb.saveState();
        cb.setLineWidth(0.6f);
        cb.setColorStroke(FOOTER_GRAY);
        cb.moveTo(46f, h - 533f);
        cb.lineTo(374f, h - 533f);
        cb.stroke();
        cb.restoreState();
        StringBuilder footerContact = new StringBuilder();
        if (address != null && !address.isBlank()) {
            footerContact.append(address);
        }
        if (contact.length() > 0) {
            if (footerContact.length() > 0) {
                footerContact.append("   —   ");
            }
            footerContact.append(contact);
        }
        if (website != null && !website.isBlank()) {
            if (footerContact.length() > 0) {
                footerContact.append("   —   ");
            }
            footerContact.append(website);
        }
        if (footerContact.length() > 0) {
            centeredAt(cb, footerContact.toString(), sans(6.5f, GRAY_TEXT), w / 2, h - 548f);
        }
        centeredAt(cb, "Établissement privé d'enseignement accrédité par le Ministère "
                        + "de l'Éducation Nationale  —  " + (schoolName != null ? schoolName : "")
                        + "   —   Page 1",
                sans(6f, GRAY_TEXT), w / 2, h - 558f);
        } finally {
            safeClose(document);
        }
    }

    // ---------- Aides de dessin ----------

    private void centeredAt(PdfContentByte cb, String text, Font font, float x, float y) {
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, new Phrase(text, font), x, y, 0);
    }

    private void leftTextAt(PdfContentByte cb, String text, Font font, float x, float y) {
        ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(text, font), x, y, 0);
    }

    private void leftWrappedAt(PdfContentByte cb, String text, Font font,
                               float x, float yTop, float width, float firstLineIndent) {
        Paragraph p = new Paragraph(text, font);
        if (firstLineIndent > 0) {
            p.setFirstLineIndent(firstLineIndent);
        }
        float leading = font.getSize() * 1.5f;
        ColumnText ct = new ColumnText(cb);
        ct.setSimpleColumn(x, yTop - 80f, x + width, yTop, leading, Element.ALIGN_LEFT);
        ct.addElement(p);
        try {
            ct.go();
        } catch (DocumentException ignored) {
            // texte non affichable : ignoré
        }
    }

    private String birthDate(com.school.dto.response.StudentResponse student) {
        return student.getBirthDate() != null
                ? student.getBirthDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                : "—";
    }

    private String extractCity(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }
        String[] parts = address.split(",");
        String city = parts[parts.length - 1].trim();
        return city.matches("^[A-Za-zÀ-ÿ' .-]+$") ? city : "";
    }

    private Image loadLogo() {
        String logoUrl = settingService.value(SettingService.SCHOOL_LOGO, "");
        if (logoUrl != null && logoUrl.startsWith("/uploads/")) {
            String relative = logoUrl.substring("/uploads/".length());
            Path uploadDir = Paths.get(appProperties.getUploadDir()).toAbsolutePath().normalize();
            File file = uploadDir.resolve(relative).normalize().toFile();
            try {
                if (file.getCanonicalPath().startsWith(uploadDir.toRealPath().toString())) {
                    return Image.getInstance(file.toURI().toURL());
                }
            } catch (Exception ignored) {
                // fichier hors du dossier uploads : ignorer
            }
        }
        try (InputStream in = getClass().getResourceAsStream("/static/school-logo.png")) {
            if (in != null) {
                return Image.getInstance(in.readAllBytes());
            }
        } catch (Exception ignored) {
            // aucun logo disponible
        }
        return null;
    }

    private Image loadStudentQr(com.school.dto.response.StudentResponse student) {
        try {
            byte[] png = qrCodeService.studentQrPng(student.getMatricule(),
                    student.getFirstName() + " " + student.getLastName(), student.getClassName());
            return Image.getInstance(png);
        } catch (Exception e) {
            return null;
        }
    }

    private Image loadPhoto(String photoUrl) {
        if (photoUrl == null || !photoUrl.startsWith("/uploads/")) {
            return null;
        }
        try {
            String relative = photoUrl.substring("/uploads/".length());
            Path uploadDir = Paths.get(appProperties.getUploadDir()).toAbsolutePath().normalize();
            File file = uploadDir.resolve(relative).normalize().toFile();
            if (file.getCanonicalPath().startsWith(uploadDir.toRealPath().toString())) {
                return Image.getInstance(file.toURI().toURL());
            }
        } catch (Exception ignored) {
            // photo manquante, hors uploads ou illisible : carte sans photo
        }
        return null;
    }

    // ---------- Excel ----------

    public void exportExcel(HttpServletResponse response, String[] headers, List<String[]> rows,
                            String title) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + sanitize(title) + ".xlsx\"");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sanitize(title).replaceAll("[^a-zA-Z0-9]", "_").substring(0,
                    Math.min(30, sanitize(title).length())));
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            int rowIdx = 1;
            for (String[] row : rows) {
                Row excelRow = sheet.createRow(rowIdx++);
                for (int i = 0; i < row.length; i++) {
                    excelRow.createCell(i).setCellValue(row[i] != null ? row[i] : "");
                }
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(response.getOutputStream());
        }
    }

    private String sanitize(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9-_]", "-");
    }

    /**
     * Relevé universitaire Excel : UE, notes, crédits, moyenne, décision.
     */
    public void universityReleveExcel(HttpServletResponse response,
                                      com.school.dto.response.LmdReleveResponse releve)
            throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"releve-" + sanitize(releve.getMatricule()) + ".xlsx\"");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Releve");
            Row headerRow = sheet.createRow(0);
            String[] headers = {"Code", "UE", "Coefficient", "Crédits", "Note /20", "Statut"};
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            int rowIdx = 1;
            for (com.school.dto.response.LmdReleveResponse.UeLine ue : releve.getUes()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(ue.getCode());
                row.createCell(1).setCellValue(ue.getName());
                row.createCell(2).setCellValue(ue.getCoefficient());
                row.createCell(3).setCellValue(ue.getCredits());
                row.createCell(4).setCellValue(ue.getNote() != null ? ue.getNote().doubleValue() : 0);
                row.createCell(5).setCellValue(ue.getNote() != null
                        && ue.getNote().compareTo(java.math.BigDecimal.TEN) >= 0 ? "VALIDÉE" : "À repasser");
            }
            // Ligne récapitulative
            Row summary = sheet.createRow(rowIdx + 1);
            summary.createCell(1).setCellValue("MOYENNE");
            summary.createCell(4).setCellValue(releve.getAverage() != null ? releve.getAverage().doubleValue() : 0);
            summary.createCell(5).setCellValue(releve.getDecision() != null ? releve.getDecision().name() : "—");
            Row credits = sheet.createRow(rowIdx + 2);
            credits.createCell(1).setCellValue("Crédits obtenus / échoués / total");
            credits.createCell(3).setCellValue(releve.getCreditsObtained() + " / "
                    + releve.getCreditsFailed() + " / " + releve.getTotalCredits());
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(response.getOutputStream());
        }
    }

    /**
     * Relevé universitaire PDF : UE/EC, notes, crédits, moyenne, décision, mention.
     */
    public void universityRelevePdf(HttpServletResponse response,
                                    com.school.dto.response.LmdReleveResponse releve)
            throws IOException {
        Document document = new Document(PageSize.A4, 36, 36, 36, 70);
        PdfWriter writer = documentTheme.init(response, "releve-" + releve.getMatricule()
                + "-" + releve.getSemester(), document);
        document.open();
        try {
            documentTheme.addHeader(document);
            documentTheme.addTitle(document, "RELEVÉ DE NOTES UNIVERSITAIRE",
                    releve.getFieldName() + (releve.getProgramName() != null ? "  •  " + releve.getProgramName() : "")
                            + "  •  Semestre " + releve.getSemester() + "  •  " + releve.getAcademicYear());

            PdfPTable info = documentTheme.infoTable(new String[][]{
                    {"Étudiant", releve.getStudentName()},
                    {"Matricule", releve.getMatricule()},
                    {"Filière", releve.getFieldName()},
                    {"Niveau", releve.getLevel() != null ? releve.getLevel().name() : "—"},
                    {"Semestre", releve.getSemester()},
                    {"Session", releve.getSession() + (releve.getSession() == 2 ? " (rattrapage)" : " (normale)")},
            });
            document.add(info);
            document.add(Chunk.NEWLINE);

            documentTheme.sectionTitle(document, "NOTES PAR UE");
            List<String[]> rows = releve.getUes().stream().map(u -> new String[]{
                    u.getCode(), u.getName(), String.valueOf(u.getCoefficient()),
                    String.valueOf(u.getCredits()),
                    u.getNote() != null ? u.getNote().toString() : "—",
                    u.getNote() != null && u.getNote().compareTo(java.math.BigDecimal.TEN) >= 0 ? "VALIDÉE" : "À repasser"
            }).toList();
            PdfPTable table = documentTheme.dataTable(
                    new String[]{"Code", "UE", "Coef.", "Crédits", "Note /20", "Statut"},
                    rows, new float[]{1.5f, 4f, 1f, 1.2f, 1.2f, 1.8f}, true);
            table.setHeaderRows(1);
            document.add(table);
            document.add(Chunk.NEWLINE);

            if (releve.getAverage() != null) {
                document.add(documentTheme.totalRow("MOYENNE", releve.getAverage().toString() + "/20",
                        SchoolDocumentTheme.PRIMARY));
            }
            document.add(Chunk.NEWLINE);
            PdfPTable summary = documentTheme.infoTable(new String[][]{
                    {"Crédits obtenus", String.valueOf(releve.getCreditsObtained())},
                    {"Crédits échoués", String.valueOf(releve.getCreditsFailed())},
                    {"Total crédits", String.valueOf(releve.getTotalCredits())},
                    {"Décision", releve.getDecision() != null ? releve.getDecision().name() : "—"},
                    {"Mention", releve.getMention() != null ? releve.getMention().name() : "—"},
            });
            document.add(summary);
            document.add(Chunk.NEWLINE);

            document.add(documentTheme.signatureBlock("Responsable de filière", "Le Directeur"));
        } finally {
            safeClose(document);
        }
    }

    /**
     * Attestation de réussite universitaire PDF, avec QR code de vérification.
     */
    public void universityAttestationPdf(HttpServletResponse response,
                                         com.school.dto.response.LmdAttestationResponse attestation)
            throws IOException {
        Document document = new Document(PageSize.A4, 40, 40, 40, 70);
        PdfWriter writer = documentTheme.init(response, "attestation-" + attestation.getMatricule(), document);
        document.open();
        try {
            documentTheme.addHeader(document);
            documentTheme.addTitle(document, "ATTESTATION DE RÉUSSITE",
                    "Année universitaire " + attestation.getAcademicYear());

            Paragraph intro = new Paragraph("Je soussigné, responsable de l'établissement, certifie que l'étudiant :",
                    SchoolDocumentTheme.body(11f));
            intro.setLeading(16f);
            document.add(intro);
            document.add(Chunk.NEWLINE);

            PdfPTable info = documentTheme.infoTable(new String[][]{
                    {"Étudiant", attestation.getStudentName()},
                    {"Matricule", attestation.getMatricule()},
                    {"Programme", attestation.getProgramName()},
                    {"Diplôme", attestation.getDiploma() != null ? attestation.getDiploma() : "—"},
                    {"Semestre", attestation.getSemester()},
                    {"Moyenne", attestation.getAverage() != null ? attestation.getAverage().toString() + "/20" : "—"},
                    {"Mention", attestation.getMention() != null ? attestation.getMention().name() : "—"},
                    {"Décision", attestation.getDecision() != null ? attestation.getDecision().name() : "—"},
            });
            info.setSpacingBefore(8);
            document.add(info);
            document.add(Chunk.NEWLINE);

            Paragraph conclusion = new Paragraph("a validé le semestre " + attestation.getSemester()
                            + " de l'année universitaire " + attestation.getAcademicYear()
                            + " et est déclaré(e) admissible au niveau supérieur.",
                    SchoolDocumentTheme.body(10.5f));
            conclusion.setSpacingBefore(8);
            document.add(conclusion);
            document.add(Chunk.NEWLINE);

            // QR code de vérification
            if (attestation.getVerificationToken() != null && !attestation.getVerificationToken().isBlank()) {
                try {
                    String payload = "ATTESTATION\nToken: " + attestation.getVerificationToken()
                            + "\nÉtudiant: " + attestation.getStudentName()
                            + "\nProgramme: " + attestation.getProgramName()
                            + "\nSemestre: " + attestation.getSemester();
                    com.google.zxing.BarcodeFormat format = com.google.zxing.BarcodeFormat.QR_CODE;
                    com.google.zxing.qrcode.QRCodeWriter qrWriter = new com.google.zxing.qrcode.QRCodeWriter();
                    com.google.zxing.common.BitMatrix matrix = qrWriter.encode(payload, format, 350, 350);
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    com.google.zxing.client.j2se.MatrixToImageWriter.writeToStream(matrix, "PNG", bos);
                    Image qr = Image.getInstance(bos.toByteArray());
                    qr.scaleAbsolute(70f, 70f);
                    qr.setAlignment(Element.ALIGN_CENTER);
                    document.add(qr);
                    Paragraph ref = new Paragraph("Document vérifiable : ATT-" + attestation.getVerificationToken(),
                            SchoolDocumentTheme.muted(8f));
                    ref.setAlignment(Element.ALIGN_CENTER);
                    document.add(ref);
                    document.add(Chunk.NEWLINE);
                } catch (Exception ignored) {
                    // QR code indisponible : document délivré sans QR
                }
            }

            document.add(Chunk.NEWLINE);
            document.add(documentTheme.signatureBlock("Le Responsable Pédagogique", "Le Directeur"));
        } finally {
            safeClose(document);
        }
    }

    /**
     * Ferme le document PDF de manière sûre : garantit un fichier finalisé même en cas d'erreur.
     */
    private void safeClose(Document document) {
        try {
            document.close();
        } catch (Exception ignored) {
            // fichier déjà fermé ou partiel : ne pas masquer l'erreur d'origine
        }
    }
}
