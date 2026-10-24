package com.school.pdf;

import com.lowagie.text.Font;

import java.awt.Color;

/**
 * Résolution de la charte graphique d'un document, alignée sur le modèle de
 * mise en page demandé.
 *
 * <p>Les constantes statiques de {@link SchoolDocumentTheme} (palette, polices)
 * restent la <b>valeur par défaut historique</b> : un document dont la
 * configuration n'est pas résolue continue de sortir exactement comme avant.
 * Cette classe ne fait que les remplacer lorsque l'administrateur a enregistré
 * une configuration.</p>
 *
 * <p>Périmètre volontairement limité au <b>format de page</b>, aux
 * <b>couleurs</b>, à la <b>typographie</b> et à l'<b>en-tête/pied de page</b> :
 * les mises en page purement graphiques (carte scolaire, certificat de
 * fréquentation dessiné au {@code PdfContentByte}) ne sont pas concernées.</p>
 */
public final class DocumentStyle {

    /** Modèle graphique retenu. */
    public enum Template {
        CLASSIC("Classique officiel"),
        MODERN("Moderne professionnel"),
        PREMIUM("Élégant premium"),
        MINIMAL("Minimaliste"),
        CUSTOM("Personnalisé");

        private final String label;

        Template(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Position du logo dans l'en-tête. */
    public enum LogoPosition { LEFT, CENTER, RIGHT }

    /** Alignement des titres. */
    public enum TitleAlign {
        LEFT { public int iText() { return com.lowagie.text.Element.ALIGN_LEFT; } },
        CENTER { public int iText() { return com.lowagie.text.Element.ALIGN_CENTER; } },
        RIGHT { public int iText() { return com.lowagie.text.Element.ALIGN_RIGHT; } };

        public abstract int iText();
    }

    /** Format de page du document. */
    public enum PageFormat { A4, A5, LETTER }

    /** Sens de la page. */
    public enum Orientation { PORTRAIT, LANDSCAPE }

    // ---------- Apparence (immutable) ----------

    public final Template template;
    public final Color primary;
    public final Color secondary;
    public final Color accent;
    public final Color heading;
    public final Color tableHeader;
    public final Color headerText;
    public final Color border;
    public final Color text;
    public final Color sectionBackground;
    public final Color footerText;

    public final String fontFamily;
    public final float bodySize;
    public final float titleSize;
    public final float subtitleSize;
    public final float tableSize;

    public final boolean showLogo;
    public final LogoPosition logoPosition;
    public final boolean showSchoolName;
    public final boolean showContact;
    public final boolean showAddress;
    public final boolean headerRule;
    public final String footerText2;
    public final String institutionalText;
    public final TitleAlign titleAlign;

    public final PageFormat pageFormat;
    public final Orientation orientation;
    public final float marginTop;
    public final float marginRight;
    public final float marginBottom;
    public final float marginLeft;
    public final float sectionSpacing;
    public final boolean pageNumbers;
    public final boolean zebraStripes;
    public final boolean tableBorders;

    private DocumentStyle(Builder b) {
        this.template = b.template;
        this.primary = b.primary;
        this.secondary = b.secondary;
        this.accent = b.accent;
        this.heading = b.heading;
        this.tableHeader = b.tableHeader;
        this.headerText = b.headerText;
        this.border = b.border;
        this.text = b.text;
        this.sectionBackground = b.sectionBackground;
        this.footerText = b.footerText;
        this.fontFamily = b.fontFamily;
        this.bodySize = b.bodySize;
        this.titleSize = b.titleSize;
        this.subtitleSize = b.subtitleSize;
        this.tableSize = b.tableSize;
        this.showLogo = b.showLogo;
        this.logoPosition = b.logoPosition;
        this.showSchoolName = b.showSchoolName;
        this.showContact = b.showContact;
        this.showAddress = b.showAddress;
        this.headerRule = b.headerRule;
        this.footerText2 = b.footerText2;
        this.institutionalText = b.institutionalText;
        this.titleAlign = b.titleAlign;
        this.pageFormat = b.pageFormat;
        this.orientation = b.orientation;
        this.marginTop = b.marginTop;
        this.marginRight = b.marginRight;
        this.marginBottom = b.marginBottom;
        this.marginLeft = b.marginLeft;
        this.sectionSpacing = b.sectionSpacing;
        this.pageNumbers = b.pageNumbers;
        this.zebraStripes = b.zebraStripes;
        this.tableBorders = b.tableBorders;
    }

    /**
     * Charte historique : exactement les constantes de {@link SchoolDocumentTheme}.
     * Utilisée tant qu'aucune configuration n'est enregistrée, et pour les
     * documents dont la mise en page est propre (carte scolaire, certificat A5).
     */
    public static DocumentStyle defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.template = template;
        b.primary = primary;
        b.secondary = secondary;
        b.accent = accent;
        b.heading = heading;
        b.tableHeader = tableHeader;
        b.headerText = headerText;
        b.border = border;
        b.text = text;
        b.sectionBackground = sectionBackground;
        b.footerText = footerText;
        b.fontFamily = fontFamily;
        b.bodySize = bodySize;
        b.titleSize = titleSize;
        b.subtitleSize = subtitleSize;
        b.tableSize = tableSize;
        b.showLogo = showLogo;
        b.logoPosition = logoPosition;
        b.showSchoolName = showSchoolName;
        b.showContact = showContact;
        b.showAddress = showAddress;
        b.headerRule = headerRule;
        b.footerText2 = footerText2;
        b.institutionalText = institutionalText;
        b.titleAlign = titleAlign;
        b.pageFormat = pageFormat;
        b.orientation = orientation;
        b.marginTop = marginTop;
        b.marginRight = marginRight;
        b.marginBottom = marginBottom;
        b.marginLeft = marginLeft;
        b.sectionSpacing = sectionSpacing;
        b.pageNumbers = pageNumbers;
        b.zebraStripes = zebraStripes;
        b.tableBorders = tableBorders;
        return b;
    }

    /** Rectangle iText du format retenu, dans le bon sens. */
    public com.lowagie.text.Rectangle pageSize() {
        com.lowagie.text.Rectangle size = switch (pageFormat) {
            case A5 -> com.lowagie.text.PageSize.A5;
            case LETTER -> com.lowagie.text.PageSize.LETTER;
            case A4 -> com.lowagie.text.PageSize.A4;
        };
        return orientation == Orientation.LANDSCAPE ? size.rotate() : size;
    }

    // ---------- Typographie ----------

    public Font body() {
        return body(bodySize);
    }

    public Font body(float size) {
        return font(fontFamily, size, Font.NORMAL, text);
    }

    public Font bold(float size) {
        return font(fontFamily, size, Font.BOLD, text);
    }

    public Font italic(float size) {
        return font(fontFamily, size, Font.ITALIC, footerText);
    }

    public Font muted(float size) {
        return font(fontFamily, size, Font.NORMAL, footerText);
    }

    public Font heading() {
        return heading(titleSize);
    }

    public Font heading(float size) {
        return font(fontFamily, size, Font.BOLD, heading);
    }

    public Font whiteBold(float size) {
        return font(fontFamily, size, Font.BOLD, headerText);
    }

    public Font tableHeaderFont() {
        return font(fontFamily, tableSize, Font.BOLD, headerText);
    }

    public Font tableBodyFont() {
        return font(fontFamily, tableSize, Font.NORMAL, text);
    }

    /**
     * Police demandée, avec repli explicite.
     *
     * <p>Seules les polices livrées avec OpenPDF (Helvetica, Times, Courier —
     * les 14 polices PDF standard) sont prises en charge : elles ne nécessitent
     * aucune intégration de fichier, donc aucun échec de rendu possible. Une
     * valeur inconnue retombe sur Helvetica, ou sur Times pour les modèles qui
     * reposent sur un empattement.</p>
     */
    /** Police d'une famille et d'un style donnés (repli Helvetica si inconnue). */
    public static Font fontOf(String family, float size, int style, Color color) {
        return font(family, size, style, color);
    }

    /** Police de repli neutre (utilisée si aucune charte n'est fournie). */
    public static Font fontDefault() {
        return com.lowagie.text.FontFactory.getFont(
                com.lowagie.text.FontFactory.HELVETICA, 11f, SchoolDocumentTheme.TEXT_DARK);
    }

    static Font font(String family, float size, int style, Color color) {
        boolean bold = (style & Font.BOLD) != 0;
        boolean italic = (style & Font.ITALIC) != 0;
        String name = switch (family == null ? "" : family.toUpperCase()) {
            case "TIMES", "TIMES_ROMAN", "SERIF" -> !bold && !italic
                    ? com.lowagie.text.FontFactory.TIMES_ROMAN
                    : bold && italic ? com.lowagie.text.FontFactory.TIMES_BOLDITALIC
                    : bold ? com.lowagie.text.FontFactory.TIMES_BOLD
                    : com.lowagie.text.FontFactory.TIMES_ITALIC;
            case "COURIER", "MONO" -> bold
                    ? com.lowagie.text.FontFactory.COURIER_BOLD
                    : com.lowagie.text.FontFactory.COURIER;
            default -> !bold && !italic
                    ? com.lowagie.text.FontFactory.HELVETICA
                    : bold ? com.lowagie.text.FontFactory.HELVETICA_BOLD
                    : com.lowagie.text.FontFactory.HELVETICA_OBLIQUE;
        };
        // OpenPDF n'expose pas de constante « gras italique » : on fabrique
        // la variante à partir du nom PostScript exact.
        if (bold && italic) {
            name = family != null && family.toUpperCase().startsWith("TIME")
                    ? "Times-BoldItalic" : "Helvetica-BoldOblique";
        }
        return com.lowagie.text.FontFactory.getFont(name, size, color);
    }

    // ---------- Construction ----------

    public static final class Builder {
        private Template template = Template.CLASSIC;
        private Color primary = SchoolDocumentTheme.PRIMARY;
        private Color secondary = SchoolDocumentTheme.ACCENT;
        private Color accent = SchoolDocumentTheme.ACCENT;
        private Color heading = SchoolDocumentTheme.PRIMARY;
        private Color tableHeader = SchoolDocumentTheme.PRIMARY;
        private Color headerText = Color.WHITE;
        private Color border = SchoolDocumentTheme.BORDER;
        private Color text = SchoolDocumentTheme.TEXT_DARK;
        private Color sectionBackground = SchoolDocumentTheme.HEADER_BG;
        private Color footerText = SchoolDocumentTheme.TEXT_MUTED;

        private String fontFamily = "HELVETICA";
        private float bodySize = 11f;
        private float titleSize = 16f;
        private float subtitleSize = 9.5f;
        private float tableSize = 9f;

        private boolean showLogo = true;
        private LogoPosition logoPosition = LogoPosition.LEFT;
        private boolean showSchoolName = true;
        private boolean showContact = true;
        private boolean showAddress = true;
        private boolean headerRule = true;
        private String footerText2 = "";
        private String institutionalText = "";
        private TitleAlign titleAlign = TitleAlign.LEFT;

        private PageFormat pageFormat = PageFormat.A4;
        private Orientation orientation = Orientation.PORTRAIT;
        private float marginTop = 32f;
        private float marginRight = 32f;
        private float marginBottom = 70f;
        private float marginLeft = 32f;
        private float sectionSpacing = 10f;
        private boolean pageNumbers = true;
        private boolean zebraStripes = true;
        private boolean tableBorders = false;

        public Builder template(Template v) { this.template = v == null ? Template.CLASSIC : v; return this; }
        public Builder primary(Color v) { this.primary = nz(v, SchoolDocumentTheme.PRIMARY); return this; }
        public Builder secondary(Color v) { this.secondary = nz(v, SchoolDocumentTheme.ACCENT); return this; }
        public Builder accent(Color v) { this.accent = nz(v, SchoolDocumentTheme.ACCENT); return this; }
        public Builder heading(Color v) { this.heading = nz(v, primary); return this; }
        public Builder tableHeader(Color v) { this.tableHeader = nz(v, primary); return this; }
        public Builder headerText(Color v) { this.headerText = nz(v, Color.WHITE); return this; }
        public Builder border(Color v) { this.border = nz(v, SchoolDocumentTheme.BORDER); return this; }
        public Builder text(Color v) { this.text = nz(v, SchoolDocumentTheme.TEXT_DARK); return this; }
        public Builder sectionBackground(Color v) { this.sectionBackground = nz(v, SchoolDocumentTheme.HEADER_BG); return this; }
        public Builder footerText(Color v) { this.footerText = nz(v, SchoolDocumentTheme.TEXT_MUTED); return this; }

        public Builder fontFamily(String v) { this.fontFamily = v == null ? "HELVETICA" : v; return this; }
        public Builder bodySize(float v) { this.bodySize = clamp(v, 7f, 14f, 11f); return this; }
        public Builder titleSize(float v) { this.titleSize = clamp(v, 12f, 26f, 16f); return this; }
        public Builder subtitleSize(float v) { this.subtitleSize = clamp(v, 7f, 16f, 9.5f); return this; }
        public Builder tableSize(float v) { this.tableSize = clamp(v, 6f, 12f, 9f); return this; }

        public Builder showLogo(boolean v) { this.showLogo = v; return this; }
        public Builder logoPosition(LogoPosition v) { this.logoPosition = v == null ? LogoPosition.LEFT : v; return this; }
        public Builder showSchoolName(boolean v) { this.showSchoolName = v; return this; }
        public Builder showContact(boolean v) { this.showContact = v; return this; }
        public Builder showAddress(boolean v) { this.showAddress = v; return this; }
        public Builder headerRule(boolean v) { this.headerRule = v; return this; }
        public Builder footerText2(String v) { this.footerText2 = v == null ? "" : v; return this; }
        public Builder institutionalText(String v) { this.institutionalText = v == null ? "" : v; return this; }
        public Builder titleAlign(TitleAlign v) { this.titleAlign = v == null ? TitleAlign.LEFT : v; return this; }

        public Builder pageFormat(PageFormat v) { this.pageFormat = v == null ? PageFormat.A4 : v; return this; }
        public Builder orientation(Orientation v) { this.orientation = v == null ? Orientation.PORTRAIT : v; return this; }
        public Builder margins(float top, float right, float bottom, float left) {
            this.marginTop = clamp(top, 14f, 90f, 32f);
            this.marginRight = clamp(right, 14f, 90f, 32f);
            this.marginBottom = clamp(bottom, 20f, 110f, 70f);
            this.marginLeft = clamp(left, 14f, 90f, 32f);
            return this;
        }
        public Builder sectionSpacing(float v) { this.sectionSpacing = clamp(v, 2f, 24f, 10f); return this; }
        public Builder pageNumbers(boolean v) { this.pageNumbers = v; return this; }
        public Builder zebraStripes(boolean v) { this.zebraStripes = v; return this; }
        public Builder tableBorders(boolean v) { this.tableBorders = v; return this; }

        public DocumentStyle build() {
            return new DocumentStyle(this);
        }

        private static Color nz(Color v, Color fallback) {
            return v == null ? fallback : v;
        }

        private static float clamp(float v, float min, float max, float fallback) {
            if (Float.isNaN(v)) {
                return fallback;
            }
            return Math.max(min, Math.min(max, v));
        }
    }
}
