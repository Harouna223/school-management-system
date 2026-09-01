package com.school.pdf;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.school.config.AppProperties;
import com.school.service.SettingService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Charte graphique documentaire unifiée de l'établissement.
 * <p>
 * Centralise : palette de couleurs institutionnelle, typographie, en-tête
 * (logo + coordonnées), pied de page numéroté, style des tableaux,
 * blocs d'information, signatures et badges de statut.
 * Tous les documents PDF de l'application utilisent ce thème.
 */
@Component
@RequiredArgsConstructor
public class SchoolDocumentTheme {

    // ---------- Palette institutionnelle ----------

    public static final Color PRIMARY = new Color(30, 58, 138);        // bleu institutionnel #1e3a8a
    public static final Color ACCENT = new Color(59, 130, 246);        // bleu clair #3b82f6
    public static final Color TEXT_DARK = new Color(30, 41, 59);       // #1e293b
    public static final Color TEXT_MUTED = new Color(100, 116, 139);   // #64748b
    public static final Color BORDER = new Color(203, 213, 225);       // #cbd5e1
    public static final Color HEADER_BG = new Color(239, 246, 255);    // #eff6ff
    public static final Color ZEBRA = new Color(248, 250, 252);        // #f8fafc
    public static final Color SUCCESS = new Color(16, 122, 87);        // vert institutionnel
    public static final Color WARNING = new Color(180, 83, 9);         // orange
    public static final Color DANGER = new Color(185, 28, 28);         // rouge

    // ---------- Typographie ----------

    public static Font body(float size) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, TEXT_DARK);
    }

    public static Font bold(float size) {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, size, TEXT_DARK);
    }

    public static Font muted(float size) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, TEXT_MUTED);
    }

    public static Font heading(float size) {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, size, PRIMARY);
    }

    public static Font whiteBold(float size) {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, size, Color.WHITE);
    }

    private final SettingService settingService;
    private final AppProperties appProperties;

    // ---------- Métadonnées établissement ----------

    public String schoolName() {
        return settingService.value(SettingService.SCHOOL_NAME, appProperties.getSchool().getName());
    }

    public String schoolAddress() {
        return settingService.value(SettingService.SCHOOL_ADDRESS, appProperties.getSchool().getAddress());
    }

    public String schoolPhone() {
        return settingService.value(SettingService.SCHOOL_PHONE, appProperties.getSchool().getPhone());
    }

    public String schoolEmail() {
        return settingService.value(SettingService.SCHOOL_EMAIL, appProperties.getSchool().getEmail());
    }

    public String schoolSlogan() {
        return settingService.value(SettingService.SCHOOL_SLOGAN, "");
    }

    public String schoolWebsite() {
        return settingService.value(SettingService.SCHOOL_WEBSITE, "");
    }

    public String academicYear() {
        return com.school.utils.CodeGenerator.currentAcademicYear();
    }

    /**
     * Initialise un document PDF : en-tête HTTP, pied de page numéroté.
     */
    public PdfWriter init(HttpServletResponse response, String filename, Document document)
            throws IOException {
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + ".pdf\"");
        PdfWriter writer = PdfWriter.getInstance(document, response.getOutputStream());
        writer.setPageEvent(new DocumentFooter(schoolName()));
        return writer;
    }

    /**
     * En-tête officiel : logo à gauche, identité de l'établissement à droite,
     * double filet bleu sous l'en-tête.
     */
    public void addHeader(Document document) {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{1.1f, 3.4f});
        header.setKeepTogether(true);

        // Cellule logo
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(0);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        logoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        Image logo = loadLogo();
        if (logo != null) {
            try {
                logo.scaleAbsolute(56f, 56f);
                logoCell.addElement(logo);
            } catch (Exception ignored) {
                logoCell.addElement(initialsFallback());
            }
        } else {
            logoCell.addElement(initialsFallback());
        }
        header.addCell(logoCell);

        // Cellule identité
        PdfPCell infoCell = new PdfPCell();
        infoCell.setBorder(0);
        infoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        infoCell.setPaddingLeft(8);
        Paragraph name = new Paragraph(schoolName() != null ? schoolName() : "Établissement scolaire",
                heading(15));
        name.setSpacingAfter(2);
        infoCell.addElement(name);
        String slogan = schoolSlogan();
        if (slogan != null && !slogan.isBlank()) {
            Paragraph slog = new Paragraph(slogan, muted(8.5f));
            slog.setSpacingAfter(3);
            infoCell.addElement(slog);
        }
        StringBuilder line = new StringBuilder();
        String address = schoolAddress();
        if (address != null && !address.isBlank()) {
            line.append(address);
        }
        String phone = schoolPhone();
        String email = schoolEmail();
        if (phone != null && !phone.isBlank()) {
            if (line.length() > 0) line.append("  •  ");
            line.append("Tél : ").append(phone);
        }
        if (email != null && !email.isBlank()) {
            if (line.length() > 0) line.append("  •  ");
            line.append(email);
        }
        if (line.length() > 0) {
            Paragraph contact = new Paragraph(line.toString(), muted(7.5f));
            contact.setSpacingAfter(1);
            infoCell.addElement(contact);
        }
        String website = schoolWebsite();
        if (website != null && !website.isBlank()) {
            infoCell.addElement(new Paragraph(website, muted(7f)));
        }
        header.addCell(infoCell);
        document.add(header);

        // Double filet bleu sous l'en-tête
        PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(100);
        PdfPCell ruleCell = new PdfPCell();
        ruleCell.setBorder(0);
        ruleCell.setBorderWidthBottom(1.6f);
        ruleCell.setBorderColorBottom(PRIMARY);
        ruleCell.setPaddingBottom(3);
        rule.addCell(ruleCell);
        document.add(rule);
        document.add(Chunk.NEWLINE);
    }

    private Element initialsFallback() {
        String name = schoolName() != null ? schoolName().trim() : "SMS";
        String initials = name.isEmpty() ? "SMS" : name.substring(0, Math.min(2, name.length())).toUpperCase();
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell(new Phrase(initials, whiteBold(18)));
        cell.setBackgroundColor(PRIMARY);
        cell.setBorder(0);
        cell.setFixedHeight(56f);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        box.addCell(cell);
        return box;
    }

    /**
     * Bloc titre du document : type de document + sous-titre.
     */
    public void addTitle(Document document, String title, String subtitle) {
        Paragraph t = new Paragraph(title, heading(16));
        t.setSpacingAfter(subtitle != null && !subtitle.isBlank() ? 2 : 8);
        document.add(t);
        if (subtitle != null && !subtitle.isBlank()) {
            Paragraph st = new Paragraph(subtitle, muted(9.5f));
            st.setSpacingAfter(8);
            document.add(st);
        }
    }

    /**
     * Titre de section.
     */
    public void sectionTitle(Document document, String text) {
        Paragraph p = new Paragraph(text, bold(10.5f));
        p.setSpacingBefore(10);
        p.setSpacingAfter(5);
        document.add(p);
    }

    /**
     * Tableau de données stylisé : en-tête bleu, texte blanc, zébrures, bordures discrètes.
     * Les colonnes numériques sont alignées à droite (heuristique : dernière colonne).
     */
    public PdfPTable dataTable(String[] headers, List<String[]> rows, float[] widths, boolean zebra) {
        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        if (widths != null) {
            table.setWidths(widths);
        }
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, whiteBold(9.5f)));
            cell.setBackgroundColor(PRIMARY);
            cell.setPadding(6);
            cell.setBorder(0);
            cell.setBorderColor(BORDER);
            table.addCell(cell);
        }
        boolean stripe = false;
        for (String[] row : rows) {
            for (int c = 0; c < row.length; c++) {
                Phrase ph = new Phrase(row[c] != null ? row[c] : "", body(9f));
                PdfPCell cell = new PdfPCell(ph);
                cell.setPadding(5);
                cell.setBorder(0);
                cell.setBorderColor(BORDER);
                cell.setBorderWidthBottom(0.6f);
                if (zebra && stripe) {
                    cell.setBackgroundColor(ZEBRA);
                }
                table.addCell(cell);
            }
            stripe = !stripe;
        }
        return table;
    }

    /**
     * Tableau d'informations clé/valeur (élève, facture, etc.).
     */
    public PdfPTable infoTable(String[][] rows) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        for (String[] row : rows) {
            PdfPCell label = new PdfPCell(new Phrase(row[0], bold(9f)));
            label.setBorder(0);
            label.setPadding(4);
            label.setBackgroundColor(HEADER_BG);
            PdfPCell value = new PdfPCell(new Phrase(row[1] != null ? row[1] : "—", body(9f)));
            value.setBorder(0);
            value.setPadding(4);
            table.addCell(label);
            table.addCell(value);
        }
        return table;
    }

    /**
     * Bloc signatures alignées (enseignants, direction, cachet).
     */
    public PdfPTable signatureBlock(String... labels) {
        PdfPTable table = new PdfPTable(labels.length);
        table.setWidthPercentage(100);
        for (String label : labels) {
            PdfPCell cell = new PdfPCell(new Phrase("\n\n\n\n" + label + "\n\n(Signature et cachet)",
                    muted(8.5f)));
            cell.setBorder(0);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
        return table;
    }

    /**
     * Badge de statut (pillule colorée discrète).
     */
    public Phrase badge(String text, Color bg) {
        Chunk c = new Chunk(" " + text + " ",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Color.WHITE));
        c.setBackground(bg, 4f, 2.5f, 4f, 2.5f);
        return new Phrase(c);
    }

    /**
     * Ligne de total mise en évidence.
     */
    public PdfPTable totalRow(String label, String value, Color color) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(60);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        PdfPCell lbl = new PdfPCell(new Phrase(label, bold(11f)));
        lbl.setBorder(0);
        lbl.setPadding(5);
        lbl.setHorizontalAlignment(Element.ALIGN_RIGHT);
        PdfPCell val = new PdfPCell(new Phrase(value, bold(11f)));
        val.setBorder(0);
        val.setPadding(5);
        val.setHorizontalAlignment(Element.ALIGN_RIGHT);
        if (color != null) {
            val.setBackgroundColor(color);
            val.setPhrase(new Phrase(value, whiteBold(11f)));
        } else {
            val.setBackgroundColor(HEADER_BG);
        }
        table.addCell(lbl);
        table.addCell(val);
        return table;
    }

    /**
     * Libellé "Généré le ..." discret.
     */
    public Paragraph generatedOn() {
        String when = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        Paragraph p = new Paragraph("Document généré le " + when, muted(7.5f));
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private Image loadLogo() {
        String logoUrl = settingService.value(SettingService.SCHOOL_LOGO, "");
        if (logoUrl != null && logoUrl.startsWith("/uploads/")) {
            String relative = logoUrl.substring("/uploads/".length());
            Path uploadDir = Paths.get(appProperties.getUploadDir()).toAbsolutePath().normalize();
            Path file = uploadDir.resolve(relative).normalize();
            try {
                if (file.toFile().getCanonicalPath().startsWith(uploadDir.toRealPath().toString())) {
                    return Image.getInstance(file.toUri().toURL());
                }
            } catch (Exception ignored) {
                // fichier hors uploads : ignorer
            }
        }
        try (InputStream in = getClass().getResourceAsStream("/static/school-logo.png")) {
            if (in != null) {
                return Image.getInstance(in.readAllBytes());
            }
        } catch (Exception ignored) {
            // aucun logo embarqué
        }
        return null;
    }

    /**
     * Événement de pied de page : nom de l'établissement, coordonnées, page X / Y.
     */
    public static class DocumentFooter extends PdfPageEventHelper {

        private final String schoolName;
        private PdfTemplate total;

        public DocumentFooter(String schoolName) {
            this.schoolName = schoolName != null ? schoolName : "";
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            total = writer.getDirectContent().createTemplate(30, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float pageWidth = document.right() - document.left();
            float y = 28;

            // Ligne discrète
            cb.saveState();
            cb.setColorStroke(TEXT_MUTED);
            cb.setLineWidth(0.5f);
            cb.moveTo(document.left(), y + 14);
            cb.lineTo(document.right(), y + 14);
            cb.stroke();
            cb.restoreState();

            // Nom + page
            try {
                BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
                cb.beginText();
                cb.setFontAndSize(bf, 7.5f);
                cb.setColorFill(TEXT_MUTED);
                cb.showTextAligned(Element.ALIGN_LEFT, schoolName, document.left(), y, 0);
                cb.showTextAligned(Element.ALIGN_RIGHT, "Page " + writer.getPageNumber() + " / ", document.right() - 14, y, 0);
                cb.endText();
            } catch (Exception ignored) {
                // police ou texte non disponible : ignorer
            }

            // Total pages
            cb.addTemplate(total, document.right() - 14 + 20, y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            try {
                total.beginText();
                total.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED), 7.5f);
                total.setColorFill(TEXT_MUTED);
                total.showText(String.valueOf(writer.getPageNumber() - 1));
                total.endText();
            } catch (Exception ignored) {
                // police ou texte non disponible : ignorer
            }
        }
    }
}
