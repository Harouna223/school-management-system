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
 * <p>
 * <b>Personnalisation</b> : les constantes publiques (palette, polices)
 * restent la charte d'origine et servent de repli. Chaque appel accepte un
 * {@link DocumentStyle} résolu par {@link DocumentStyleService} : le rendu
 * suit alors la configuration enregistrée par l'administrateur. Un document
 * généré sans style explicite sort exactement comme avant.
 */
@Component
@RequiredArgsConstructor
public class SchoolDocumentTheme {

    // ---------- Palette institutionnelle (charte d'origine) ----------

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

    // ---------- Typographie (charte d'origine) ----------

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
    private final com.school.service.AcademicCalendarService academicCalendarService;
    private final DocumentStyleService documentStyleService;

    // ---------- Résolution du style ----------

    /** Style d'un type de document (thème actif + option de ce type). */
    public DocumentStyle styleFor(String documentType) {
        return documentStyleService.resolve(documentType);
    }

    /** Thème actif, sans option de type de document. */
    public DocumentStyle style() {
        return documentStyleService.activeStyle();
    }

    /** Version du thème utilisée — épinglée sur les documents délivrés. */
    public Long activeVersionId() {
        return documentStyleService.activeVersionId();
    }

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
        // Source de vérité : table academic_years (la déduction depuis l'année
        // civile affichait l'année N+1 sur tous les PDF de janvier à août).
        return academicCalendarService.yearLabel(null);
    }

    // ---------- Initialisation ----------

    /**
     * Initialise un document PDF : en-tête HTTP, pied de page numéroté.
     */
    public PdfWriter init(HttpServletResponse response, String filename, Document document)
            throws IOException {
        return init(response, filename, document, DocumentStyle.defaults());
    }

    /**
     * Initialise un document PDF avec la charte donnée.
     */
    public PdfWriter init(HttpServletResponse response, String filename, Document document,
                          DocumentStyle style) throws IOException {
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + ".pdf\"");
        PdfWriter writer = PdfWriter.getInstance(document, response.getOutputStream());
        DocumentStyle s = style != null ? style : DocumentStyle.defaults();
        writer.setPageEvent(new DocumentFooter(schoolName(), s));
        return writer;
    }

    // ---------- En-tête ----------

    /** En-tête avec la charte d'origine. */
    public void addHeader(Document document) {
        addHeader(document, DocumentStyle.defaults());
    }

    /**
     * En-tête officiel : logo, identité de l'établissement et filet de
     * séparation. Position du logo, éléments affichés et couleur du filet
     * viennent de la configuration ; le modèle « Moderne » centre l'ensemble.
     */
    public void addHeader(Document document, DocumentStyle style) {
        DocumentStyle s = style != null ? style : DocumentStyle.defaults();
        boolean centered = s.logoPosition == DocumentStyle.LogoPosition.CENTER;

        PdfPTable header = new PdfPTable(centered ? 1 : 2);
        header.setWidthPercentage(100);
        if (!centered) {
            header.setWidths(s.logoPosition == DocumentStyle.LogoPosition.LEFT
                    ? new float[]{1.1f, 3.4f}
                    : new float[]{3.4f, 1.1f});
        }
        header.setKeepTogether(true);

        Element logoElement = null;
        if (s.showLogo) {
            Image logo = loadLogo();
            if (logo != null) {
                try {
                    logo.scaleAbsolute(56f, 56f);
                    logoElement = logo;
                } catch (Exception ignored) {
                    logoElement = initialsFallback(s);
                }
            } else {
                logoElement = initialsFallback(s);
            }
        }

        PdfPCell infoCell = new PdfPCell();
        infoCell.setBorder(0);
        infoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        infoCell.setPaddingLeft(centered ? 0 : 8);
        infoCell.setHorizontalAlignment(centered ? Element.ALIGN_CENTER : Element.ALIGN_LEFT);
        if (s.showSchoolName) {
            Paragraph name = new Paragraph(
                    schoolName() != null ? schoolName() : "Établissement scolaire",
                    s.heading(centered ? s.titleSize - 2 : 15));
            name.setSpacingAfter(2);
            if (centered) {
                name.setAlignment(Element.ALIGN_CENTER);
            }
            infoCell.addElement(name);
        }
        String slogan = schoolSlogan();
        if (slogan != null && !slogan.isBlank()) {
            Paragraph slog = new Paragraph(slogan, s.muted(8.5f));
            slog.setSpacingAfter(3);
            if (centered) {
                slog.setAlignment(Element.ALIGN_CENTER);
            }
            infoCell.addElement(slog);
        }
        if (s.showContact || s.showAddress) {
            StringBuilder line = new StringBuilder();
            if (s.showAddress) {
                String address = schoolAddress();
                if (address != null && !address.isBlank()) {
                    line.append(address);
                }
            }
            if (s.showContact) {
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
            }
            if (line.length() > 0) {
                Paragraph contact = new Paragraph(line.toString(), s.muted(7.5f));
                contact.setSpacingAfter(1);
                if (centered) {
                    contact.setAlignment(Element.ALIGN_CENTER);
                }
                infoCell.addElement(contact);
            }
            String website = schoolWebsite();
            if (s.showContact && website != null && !website.isBlank()) {
                Paragraph web = new Paragraph(website, s.muted(7f));
                if (centered) {
                    web.setAlignment(Element.ALIGN_CENTER);
                }
                infoCell.addElement(web);
            }
        }

        if (centered) {
            if (logoElement != null) {
                header.addCell(centeredLogoCell(logoElement));
            }
            header.addCell(infoCell);
        } else if (s.logoPosition == DocumentStyle.LogoPosition.LEFT) {
            header.addCell(sideLogoCell(logoElement));
            header.addCell(infoCell);
        } else {
            header.addCell(infoCell);
            header.addCell(sideLogoCell(logoElement));
        }
        document.add(header);

        // Filet de séparation
        if (s.headerRule) {
            PdfPTable rule = new PdfPTable(1);
            rule.setWidthPercentage(100);
            PdfPCell ruleCell = new PdfPCell();
            ruleCell.setBorder(0);
            ruleCell.setBorderWidthBottom(s.template == DocumentStyle.Template.MINIMAL ? 0.8f : 1.6f);
            ruleCell.setBorderColorBottom(s.primary);
            ruleCell.setPaddingBottom(3);
            rule.addCell(ruleCell);
            document.add(rule);
        }
        document.add(Chunk.NEWLINE);
    }

    /** Cellule du logo dans un en-tête à deux colonnes. */
    private PdfPCell sideLogoCell(Element logoElement) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(0);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        if (logoElement != null) {
            cell.addElement(logoElement);
        }
        return cell;
    }

    /** Cellule du logo au-dessus de l'identité (modèle centré). */
    private PdfPCell centeredLogoCell(Element logoElement) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(0);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPaddingBottom(4);
        if (logoElement != null) {
            cell.addElement(logoElement);
        }
        return cell;
    }

    /**
     * Initiales de repli de la marque « Sahel Edu », affichées sur les documents
     * PDF quand aucun nom d'établissement n'est configuré (première lettre de
     * chacun des deux mots : <b>S</b>ahel + <b>E</b>du).
     */
    static final String BRAND_INITIALS = "SE";

    /**
     * Initiales affichées dans le cartouche du logo. Deux branches volontairement
     * distinctes :
     * <ul>
     *   <li>aucun nom d'établissement ({@code null} ou blanc) → {@link #BRAND_INITIALS} ;</li>
     *   <li>un nom est configuré → ses <b>deux premières lettres</b> (comportement
     *       historique conservé : « École Les Palmiers » → « ÉC »).</li>
     * </ul>
     * ⚠️ Ne pas « simplifier » en prenant les deux premiers caractères de la marque :
     * cela donnerait « SA » au lieu de « SE ».
     * <p>Statique et package-private afin d'être testable sans contexte Spring.
     */
    static String initialsFor(String schoolName) {
        String configured = schoolName == null ? "" : schoolName.trim();
        return configured.isEmpty()
                ? BRAND_INITIALS
                : configured.substring(0, Math.min(2, configured.length())).toUpperCase();
    }

    private Element initialsFallback() {
        return initialsFallback(DocumentStyle.defaults());
    }

    private Element initialsFallback(DocumentStyle s) {
        String initials = initialsFor(schoolName());
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell(new Phrase(initials, s.whiteBold(18)));
        cell.setBackgroundColor(s.primary);
        cell.setBorder(0);
        cell.setFixedHeight(56f);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        box.addCell(cell);
        return box;
    }

    // ---------- Titres ----------

    /** Bloc titre du document (charte d'origine). */
    public void addTitle(Document document, String title, String subtitle) {
        addTitle(document, title, subtitle, DocumentStyle.defaults());
    }

    /**
     * Bloc titre du document, surcharges optionnelles comprises.
     *
     * <p>C'est le point d'entrée à privilégier : c'est le seul qui affiche le
     * <b>texte institutionnel</b> optionnel ({@code institutionalText}), placé
     * juste après le sous-titre. Le laisser hors de {@code addTitle(...,4 args)}
     * rendrait le réglage enregistré mais invisible sur le papier.</p>
     */
    public void addTitle(Document document, String title, DocumentStyle style) {
        addTitle(document, title, null, style, null);
    }

    /**
     * Bloc titre du document : type de document + sous-titre, alignés selon la
     * configuration. Le modèle « Moderne » souligne le titre ; « Premium »
     * l'encadre.
     */
    public void addTitle(Document document, String title, String subtitle, DocumentStyle style) {
        addTitle(document, title, subtitle, style, null);
    }

    /**
     * Bloc titre complet : {@code subtitle} et {@code optionalText} peuvent être
     * nuls. Le second correspond au texte institutionnel libre défini dans les
     * options d'en-tête (slogan ou mention officielle).
     */
    public void addTitle(Document document, String title, String subtitle,
                         DocumentStyle style, String optionalText) {
        DocumentStyle s = style != null ? style : DocumentStyle.defaults();
        boolean hasSubtitle = subtitle != null && !subtitle.isBlank();
        boolean hasOptional = optionalText != null && !optionalText.isBlank();

        Paragraph t = new Paragraph(title, s.heading(s.titleSize));
        t.setAlignment(s.titleAlign.iText());
        t.setSpacingAfter(hasSubtitle ? 2 : 8);
        if (s.template == DocumentStyle.Template.MODERN) {
            t.setSpacingBefore(4);
        }
        document.add(t);

        if (s.template == DocumentStyle.Template.MODERN) {
            // Filet d'accent sous le titre, centré ou aligné avec lui.
            PdfPTable underline = new PdfPTable(1);
            underline.setWidthPercentage(s.titleAlign == DocumentStyle.TitleAlign.CENTER ? 24 : 34);
            if (s.titleAlign != DocumentStyle.TitleAlign.CENTER) {
                underline.setHorizontalAlignment(
                        s.titleAlign == DocumentStyle.TitleAlign.RIGHT ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            } else {
                underline.setHorizontalAlignment(Element.ALIGN_CENTER);
            }
            PdfPCell c = new PdfPCell();
            c.setBorder(0);
            c.setBorderWidthBottom(2f);
            c.setBorderColorBottom(s.accent);
            c.setFixedHeight(4f);
            underline.addCell(c);
            document.add(underline);
        }

        if (hasSubtitle) {
            if (s.template == DocumentStyle.Template.PREMIUM) {
                PdfPTable box = new PdfPTable(1);
                box.setWidthPercentage(100);
                PdfPCell c = new PdfPCell(new Phrase(subtitle, s.muted(s.subtitleSize)));
                c.setBorder(0);
                c.setBorderWidthLeft(2.5f);
                c.setBorderColorLeft(s.accent);
                c.setPaddingLeft(6);
                c.setPaddingTop(3);
                c.setPaddingBottom(3);
                c.setBackgroundColor(s.sectionBackground);
                c.setHorizontalAlignment(s.titleAlign.iText());
                box.addCell(c);
                box.setSpacingAfter(6);
                document.add(box);
            } else {
                Paragraph st = new Paragraph(subtitle, s.muted(s.subtitleSize));
                st.setAlignment(s.titleAlign.iText());
                st.setSpacingAfter(8);
                document.add(st);
            }
        }

        // Texte institutionnel libre : rendu comme une mention distincte, sous le
        // sous-titre, dans un corps plus petit. Il ne remplace jamais le titre.
        if (hasOptional) {
            Paragraph note = new Paragraph(optionalText, s.muted(Math.max(7f, s.subtitleSize - 1f)));
            note.setAlignment(s.titleAlign.iText());
            note.setSpacingBefore(hasSubtitle ? 2 : 4);
            note.setSpacingAfter(6);
            document.add(note);
        }
    }

    /** Titre de section (charte d'origine). */
    public void sectionTitle(Document document, String text) {
        sectionTitle(document, text, DocumentStyle.defaults());
    }

    /**
     * Titre de section. « Moderne » l'écrit dans la couleur d'accent,
     * « Premium » le fait précéder d'un filet fin.
     */
    public void sectionTitle(Document document, String text, DocumentStyle style) {
        DocumentStyle s = style != null ? style : DocumentStyle.defaults();
        Font sectionFont = s.template == DocumentStyle.Template.MODERN
                ? DocumentStyle.font(s.fontFamily, 10.5f, com.lowagie.text.Font.BOLD, s.accent)
                : s.bold(10.5f);
        Paragraph p = new Paragraph(text, sectionFont);
        p.setSpacingBefore(Math.max(6, s.sectionSpacing));
        p.setSpacingAfter(Math.max(4, s.sectionSpacing / 2f));
        if (s.template == DocumentStyle.Template.MINIMAL) {
            p.setSpacingBefore(6);
        }
        document.add(p);
    }

    // ---------- Tableaux ----------

    /** Tableau de données (charte d'origine). */
    public PdfPTable dataTable(String[] headers, List<String[]> rows, float[] widths, boolean zebra) {
        return dataTable(headers, rows, widths, zebra, DocumentStyle.defaults());
    }

    /**
     * Tableau de données stylisé : en-tête plein, texte de contraste, zébrures
     * et bordures optionnelles. Le zébrage n'est appliqué que si le style
     * l'autorise <b>et</b> que l'appelant le demande.
     */
    public PdfPTable dataTable(String[] headers, List<String[]> rows, float[] widths,
                               boolean zebra, DocumentStyle style) {
        DocumentStyle s = style != null ? style : DocumentStyle.defaults();
        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        if (widths != null) {
            table.setWidths(widths);
        }
        boolean borderlessHeaders = s.template == DocumentStyle.Template.PREMIUM
                || s.template == DocumentStyle.Template.MINIMAL;
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, s.tableHeaderFont()));
            if (borderlessHeaders) {
                cell.setBackgroundColor(s.sectionBackground);
                cell.setBorderWidthBottom(1f);
                cell.setBorderColorBottom(s.primary);
                cell.setBorderWidthTop(0);
                cell.setBorderWidthLeft(0);
                cell.setBorderWidthRight(0);
            } else {
                cell.setBackgroundColor(s.tableHeader);
                cell.setBorder(0);
            }
            cell.setPadding(6);
            cell.setBorderColor(s.border);
            table.addCell(cell);
        }
        boolean stripe = false;
        for (String[] row : rows) {
            for (int c = 0; c < row.length; c++) {
                Phrase ph = new Phrase(row[c] != null ? row[c] : "", s.tableBodyFont());
                PdfPCell cell = new PdfPCell(ph);
                cell.setPadding(5);
                if (s.tableBorders) {
                    cell.setBorderWidth(0.5f);
                    cell.setBorderColor(s.border);
                } else {
                    cell.setBorder(0);
                    cell.setBorderWidthBottom(0.6f);
                    cell.setBorderColor(s.border);
                }
                if (zebra && stripe && s.zebraStripes) {
                    cell.setBackgroundColor(s.sectionBackground);
                }
                table.addCell(cell);
            }
            stripe = !stripe;
        }
        return table;
    }

    /** Tableau clé/valeur (charte d'origine). */
    public PdfPTable infoTable(String[][] rows) {
        return infoTable(rows, DocumentStyle.defaults());
    }

    /**
     * Tableau d'informations clé/valeur (élève, facture, etc.).
     */
    public PdfPTable infoTable(String[][] rows, DocumentStyle style) {
        DocumentStyle s = style != null ? style : DocumentStyle.defaults();
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        for (String[] row : rows) {
            PdfPCell label = new PdfPCell(new Phrase(row[0], s.bold(s.tableSize)));
            label.setBorder(0);
            label.setPadding(4);
            label.setBackgroundColor(s.sectionBackground);
            PdfPCell value = new PdfPCell(new Phrase(row[1] != null ? row[1] : "—", s.tableBodyFont()));
            value.setBorder(0);
            value.setPadding(4);
            table.addCell(label);
            table.addCell(value);
        }
        return table;
    }

    /** Bloc signatures (charte d'origine). */
    public PdfPTable signatureBlock(String... labels) {
        return signatureBlock(DocumentStyle.defaults(), labels);
    }

    /**
     * Bloc signatures alignées (enseignants, direction, cachet).
     */
    public PdfPTable signatureBlock(DocumentStyle style, String... labels) {
        DocumentStyle s = style != null ? style : DocumentStyle.defaults();
        PdfPTable table = new PdfPTable(labels.length);
        table.setWidthPercentage(100);
        for (String label : labels) {
            PdfPCell cell = new PdfPCell(new Phrase("\n\n\n\n" + label + "\n\n(Signature et cachet)",
                    s.muted(8.5f)));
            cell.setBorder(0);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
        return table;
    }

    /** Badge de statut (charte d'origine). */
    public Phrase badge(String text, Color bg) {
        return badge(text, bg, DocumentStyle.defaults());
    }

    /**
     * Badge de statut (pillule colorée discrète).
     */
    public Phrase badge(String text, Color bg, DocumentStyle style) {
        DocumentStyle s = style != null ? style : DocumentStyle.defaults();
        Chunk c = new Chunk(" " + text + " ",
                DocumentStyle.font(s.fontFamily, 8.5f, com.lowagie.text.Font.BOLD, s.headerText));
        c.setBackground(bg, 4f, 2.5f, 4f, 2.5f);
        return new Phrase(c);
    }

    /** Ligne de total (charte d'origine). */
    public PdfPTable totalRow(String label, String value, Color color) {
        return totalRow(label, value, color, DocumentStyle.defaults());
    }

    /**
     * Ligne de total mise en évidence.
     */
    public PdfPTable totalRow(String label, String value, Color color, DocumentStyle style) {
        DocumentStyle s = style != null ? style : DocumentStyle.defaults();
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(60);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        PdfPCell lbl = new PdfPCell(new Phrase(label, s.bold(11f)));
        lbl.setBorder(0);
        lbl.setPadding(5);
        lbl.setHorizontalAlignment(Element.ALIGN_RIGHT);
        PdfPCell val = new PdfPCell(new Phrase(value, s.bold(11f)));
        val.setBorder(0);
        val.setPadding(5);
        val.setHorizontalAlignment(Element.ALIGN_RIGHT);
        if (color != null) {
            val.setBackgroundColor(color);
            val.setPhrase(new Phrase(value, s.whiteBold(11f)));
        } else {
            val.setBackgroundColor(s.sectionBackground);
        }
        table.addCell(lbl);
        table.addCell(val);
        return table;
    }

    /** « Généré le … » (charte d'origine). */
    public Paragraph generatedOn() {
        return generatedOn(DocumentStyle.defaults());
    }

    /**
     * Libellé « Généré le … » discret.
     */
    public Paragraph generatedOn(DocumentStyle style) {
        DocumentStyle s = style != null ? style : DocumentStyle.defaults();
        String when = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        Paragraph p = new Paragraph("Document généré le " + when, s.muted(7.5f));
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
        private final DocumentStyle style;
        private PdfTemplate total;

        public DocumentFooter(String schoolName) {
            this(schoolName, DocumentStyle.defaults());
        }

        public DocumentFooter(String schoolName, DocumentStyle style) {
            this.schoolName = schoolName != null ? schoolName : "";
            this.style = style != null ? style : DocumentStyle.defaults();
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            total = writer.getDirectContent().createTemplate(30, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float y = 28;

            // Ligne discrète
            cb.saveState();
            cb.setColorStroke(style.footerText);
            cb.setLineWidth(0.5f);
            cb.moveTo(document.left(), y + 14);
            cb.lineTo(document.right(), y + 14);
            cb.stroke();
            cb.restoreState();

            // Nom + pied de page personnalisé + numéro de page
            try {
                BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
                cb.beginText();
                cb.setFontAndSize(bf, 7.5f);
                cb.setColorFill(style.footerText);
                cb.showTextAligned(Element.ALIGN_LEFT, schoolName, document.left(), y, 0);
                String custom = style.footerText2;
                if (custom != null && !custom.isBlank()) {
                    cb.showTextAligned(Element.ALIGN_CENTER, custom, (document.left() + document.right()) / 2f, y, 0);
                }
                if (style.pageNumbers) {
                    cb.showTextAligned(Element.ALIGN_RIGHT,
                            "Page " + writer.getPageNumber() + " / ", document.right() - 14, y, 0);
                }
                cb.endText();
            } catch (Exception ignored) {
                // police ou texte non disponible : ignorer
            }

            // Total pages
            if (style.pageNumbers) {
                cb.addTemplate(total, document.right() - 14 + 20, y);
            }
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            if (!style.pageNumbers) {
                return;
            }
            try {
                total.beginText();
                total.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED), 7.5f);
                total.setColorFill(style.footerText);
                total.showText(String.valueOf(writer.getPageNumber() - 1));
                total.endText();
            } catch (Exception ignored) {
                // police ou texte non disponible : ignorer
            }
        }
    }
}
