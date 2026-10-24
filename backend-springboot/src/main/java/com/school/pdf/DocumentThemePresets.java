package com.school.pdf;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Préréglages des modèles graphiques et des palettes de couleurs.
 *
 * <p><b>Pourquoi une configuration partielle</b> : chaque modèle ne redéfinit
 * que ce qui le distingue. Les autres valeurs reprennent celles du thème actif
 * de l'établissement, si bien que choisir « Minimaliste » ne fait pas perdre
 * les couleurs institutionnelles : seules la typographie, les filets et les
 * tailles changent. Appliquer un modèle est donc cumulable avec une palette.</p>
 *
 * <p>Aucune couleur n'est imposée : {@code base} (le thème courant) sert
 * toujours de repli, et l'administrateur peut ensuite tout ajuster à la main.</p>
 */
public final class DocumentThemePresets {

    private DocumentThemePresets() {
    }

    /** Palette nommée, exposée au frontend pour l'affichage des vignettes. */
    public record Palette(String code, String label, String primary, String secondary,
                          String accent, String heading, String tableHeader, String border,
                          String text, String sectionBackground, String footerText) {
    }

    /** Modèle nommé + description lisible (affichée sous la vignette). */
    public record TemplateInfo(String code, String label, String description) {
    }

    public static final java.util.List<Palette> PALETTES = java.util.List.of(
            new Palette("BLUE_INSTITUTIONAL", "Bleu institutionnel",
                    "#1e3a8a", "#3b82f6", "#2563eb", "#1e3a8a", "#1e3a8a",
                    "#cbd5e1", "#1e293b", "#eff6ff", "#64748b"),
            new Palette("ACADEMIC_GREEN", "Vert académique",
                    "#14532d", "#16a34a", "#15803d", "#14532d", "#166534",
                    "#c7d2c9", "#1c2b22", "#ecfdf5", "#5f6f66"),
            new Palette("ELEGANT_BURGUNDY", "Bordeaux élégant",
                    "#7f1d1d", "#b91c1c", "#9f1239", "#7f1d1d", "#881337",
                    "#e2c9c9", "#2b1a1a", "#fef2f2", "#7a5f5f"),
            new Palette("NIGHT_BLUE_GOLD", "Bleu nuit et or",
                    "#0f2a4e", "#b39058", "#b39058", "#0f2a4e", "#0f2a4e",
                    "#d0d5dd", "#28303a", "#f6f3ec", "#7a808a"),
            new Palette("PROFESSIONAL_GREY", "Gris professionnel",
                    "#334155", "#64748b", "#475569", "#0f172a", "#334155",
                    "#cbd5e1", "#1e293b", "#f1f5f9", "#64748b")
    );

    public static final java.util.List<TemplateInfo> TEMPLATES = java.util.List.of(
            new TemplateInfo("CLASSIC", "Classique officiel",
                    "Sobre et institutionnel : logo à gauche, identité à droite, double filet, titres alignés à gauche."),
            new TemplateInfo("MODERN", "Moderne professionnel",
                    "En-tête centré, titre centré et souligné, tableaux à bandeau plein, interlignes plus aérés."),
            new TemplateInfo("PREMIUM", "Élégant premium",
                    "Empattement, titre centré encadré, marges larges, filets fins et typographie plus grande."),
            new TemplateInfo("MINIMAL", "Minimaliste",
                    "Épuré : pas de filet, pas de zébrage, marges réduites, tailles plus petites (impression économique)."),
            new TemplateInfo("CUSTOM", "Personnalisé",
                    "Réglage manuel : chaque paramètre est repris tel qu'enregistré, sans préréglage.")
    );

    /**
     * Applique un modèle sur un thème de base.
     *
     * @param template code du modèle (voir {@link #TEMPLATES})
     * @param base     thème courant, utilisé pour tout ce que le modèle ne fixe pas
     * @return une nouvelle instance, {@code base} n'est jamais modifiée
     */
    public static DocumentStyle applyTemplate(String template, DocumentStyle base) {
        DocumentStyle.Builder b = base.toBuilder();
        String code = template == null ? "CLASSIC" : template.toUpperCase(Locale.ROOT);
        switch (code) {
            case "MODERN" -> b
                    .template(DocumentStyle.Template.MODERN)
                    .logoPosition(DocumentStyle.LogoPosition.CENTER)
                    .titleAlign(DocumentStyle.TitleAlign.CENTER)
                    .fontFamily("HELVETICA")
                    .bodySize(10.5f)
                    .titleSize(17f)
                    .subtitleSize(10f)
                    .tableSize(9f)
                    .margins(36f, 36f, 74f, 36f)
                    .sectionSpacing(14f)
                    .headerRule(true)
                    .zebraStripes(true)
                    .tableBorders(false);
            case "PREMIUM" -> b
                    .template(DocumentStyle.Template.PREMIUM)
                    .logoPosition(DocumentStyle.LogoPosition.LEFT)
                    .titleAlign(DocumentStyle.TitleAlign.CENTER)
                    .fontFamily("TIMES")
                    .bodySize(11.5f)
                    .titleSize(18f)
                    .subtitleSize(10.5f)
                    .tableSize(9.5f)
                    .margins(48f, 48f, 84f, 48f)
                    .sectionSpacing(16f)
                    .headerRule(true)
                    .zebraStripes(false)
                    .tableBorders(true);
            case "MINIMAL" -> b
                    .template(DocumentStyle.Template.MINIMAL)
                    .logoPosition(DocumentStyle.LogoPosition.LEFT)
                    .titleAlign(DocumentStyle.TitleAlign.LEFT)
                    .fontFamily("HELVETICA")
                    .bodySize(9.5f)
                    .titleSize(14f)
                    .subtitleSize(8.5f)
                    .tableSize(8f)
                    .margins(24f, 24f, 50f, 24f)
                    .sectionSpacing(6f)
                    .headerRule(false)
                    .zebraStripes(false)
                    .tableBorders(false);
            case "CUSTOM" -> b.template(DocumentStyle.Template.CUSTOM);
            case "CLASSIC" -> b
                    .template(DocumentStyle.Template.CLASSIC)
                    .logoPosition(DocumentStyle.LogoPosition.LEFT)
                    .titleAlign(DocumentStyle.TitleAlign.LEFT)
                    .fontFamily("HELVETICA")
                    .bodySize(11f)
                    .titleSize(16f)
                    .subtitleSize(9.5f)
                    .tableSize(9f)
                    .margins(32f, 32f, 70f, 32f)
                    .sectionSpacing(10f)
                    .headerRule(true)
                    .zebraStripes(true)
                    .tableBorders(false);
            default -> b.template(DocumentStyle.Template.CUSTOM);
        }
        return b.build();
    }

    /**
     * Applique une palette (couleurs d'en-tête et d'identité uniquement).
     *
     * @param code palette nommée, ou « CUSTOM » pour ne rien changer
     * @return le thème mis à jour
     */
    public static DocumentStyle applyPalette(String code, DocumentStyle base) {
        if (code == null) {
            return base;
        }
        Palette p = PALETTES.stream()
                .filter(x -> x.code().equals(code.toUpperCase(Locale.ROOT)))
                .findFirst()
                .orElse(null);
        if (p == null) {
            return base;
        }
        return base.toBuilder()
                .primary(hex(p.primary()))
                .secondary(hex(p.secondary()))
                .accent(hex(p.accent()))
                .heading(hex(p.heading()))
                .tableHeader(hex(p.tableHeader()))
                .border(hex(p.border()))
                .text(hex(p.text()))
                .sectionBackground(hex(p.sectionBackground()))
                .footerText(hex(p.footerText()))
                .build();
    }

    /** Palette au format carte {@code code -> #rrggbb}, pour l'API. */
    public static Map<String, Object> paletteAsMap(Palette p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", p.code());
        m.put("label", p.label());
        m.put("primary", p.primary());
        m.put("secondary", p.secondary());
        m.put("accent", p.accent());
        m.put("heading", p.heading());
        m.put("tableHeader", p.tableHeader());
        m.put("border", p.border());
        m.put("text", p.text());
        m.put("sectionBackground", p.sectionBackground());
        m.put("footerText", p.footerText());
        return m;
    }

    /**
     * Convertit {@code #rrggbb} (ou {@code #rgb}) en couleur. Toute valeur
     * invalide renvoie {@code null} : l'appelant décide du repli.
     */
    public static Color hex(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.startsWith("#")) {
            v = v.substring(1);
        }
        if (v.length() == 3) {
            v = "" + v.charAt(0) + v.charAt(0) + v.charAt(1) + v.charAt(1) + v.charAt(2) + v.charAt(2);
        }
        if (!v.matches("[0-9a-fA-F]{6}")) {
            return null;
        }
        return new Color(Integer.parseInt(v.substring(0, 2), 16),
                Integer.parseInt(v.substring(2, 4), 16),
                Integer.parseInt(v.substring(4, 6), 16));
    }

    /** Formate une couleur en {@code #rrggbb} minuscule (format de stockage). */
    public static String toHex(Color c) {
        if (c == null) {
            return null;
        }
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
