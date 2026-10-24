package com.school.pdf;

import com.school.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Lecture, écriture et <b>validation</b> de la configuration de présentation.
 *
 * <p>Toute valeur qui entre dans un PDF passe par ici. Le parseur ne renvoie
 * jamais une valeur brute : chaque champ est typé, borné, et retombe sur la
 * valeur par défaut en cas d'absence. Une couleur mal formée est refusée par
 * {@link #validate(JsonNode)} avant l'enregistrement, pas silencieusement
 * remplacée à la génération.</p>
 *
 * <p>Le JSON est la seule source de vérité : il est écrit tel quel en base
 * ({@code config_json}) et relu pour construire un {@link DocumentStyle}.
 * Aucune donnée métier (notes, montants) n'y transite jamais.</p>
 */
public final class DocumentStyleCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DocumentStyleCodec() {
    }

    // ---------- Champs reconnus ----------

    public static final String[] COLOR_FIELDS = {
            "primary", "secondary", "accent", "heading", "tableHeader", "headerText",
            "border", "text", "sectionBackground", "footerText"
    };

    public static final String[] FONT_FAMILIES = {"HELVETICA", "TIMES", "COURIER"};

    public static final String[] PAGE_FORMATS = {"A4", "A5", "LETTER"};

    // ---------- Décodage ----------

    /**
     * Construit un thème depuis un JSON (null → charte par défaut).
     * Ne lève jamais d'exception : un document doit toujours pouvoir sortir.
     */
    public static DocumentStyle decode(JsonNode root) {
        DocumentStyle.Builder b = DocumentStyle.builder();
        if (root == null || root.isNull() || !root.isObject()) {
            return b.build();
        }

        // 1. Modèle : sert de base (logo, alignement, tailles, marges).
        String template = text(root, "template", "CLASSIC");
        DocumentStyle style = DocumentThemePresets.applyTemplate(template, b.build());
        b = style.toBuilder();

        // 2. Palette nommée : couleurs institutionnelles, avant les
        //    éventuelles retouches individuelles.
        String palette = text(root, "palette", null);
        if (palette != null && !"CUSTOM".equalsIgnoreCase(palette)) {
            b = DocumentThemePresets.applyPalette(palette, b.build()).toBuilder();
        }

        // 3. Couleurs individuelles
        JsonNode colors = root.get("colors");
        if (colors != null && colors.isObject()) {
            applyColor(colors, "primary", b);
            applyColor(colors, "secondary", b);
            applyColor(colors, "accent", b);
            applyColor(colors, "heading", b);
            applyColor(colors, "tableHeader", b);
            applyColor(colors, "headerText", b);
            applyColor(colors, "border", b);
            applyColor(colors, "text", b);
            applyColor(colors, "sectionBackground", b);
            applyColor(colors, "footerText", b);
        }

        // 4. Typographie
        JsonNode typo = root.get("typography");
        if (typo != null && typo.isObject()) {
            String font = text(typo, "fontFamily", null);
            if (font != null && isFontFamily(font)) {
                b.fontFamily(font.toUpperCase(Locale.ROOT));
            }
            b.bodySize(num(typo, "bodySize", Float.NaN));
            b.titleSize(num(typo, "titleSize", Float.NaN));
            b.subtitleSize(num(typo, "subtitleSize", Float.NaN));
            b.tableSize(num(typo, "tableSize", Float.NaN));
        }

        // 5. Mise en page
        JsonNode layout = root.get("layout");
        if (layout != null && layout.isObject()) {
            b.margins(
                    num(layout, "marginTop", b.build().marginTop),
                    num(layout, "marginRight", b.build().marginRight),
                    num(layout, "marginBottom", b.build().marginBottom),
                    num(layout, "marginLeft", b.build().marginLeft));
            b.sectionSpacing(num(layout, "sectionSpacing", b.build().sectionSpacing));
            String format = text(layout, "pageFormat", null);
            if (format != null && isPageFormat(format)) {
                b.pageFormat(DocumentStyle.PageFormat.valueOf(format.toUpperCase(Locale.ROOT)));
            }
            String orientation = text(layout, "orientation", null);
            if ("LANDSCAPE".equalsIgnoreCase(orientation)) {
                b.orientation(DocumentStyle.Orientation.LANDSCAPE);
            } else if ("PORTRAIT".equalsIgnoreCase(orientation)) {
                b.orientation(DocumentStyle.Orientation.PORTRAIT);
            }
        }

        // 6. En-tête / pied de page
        //
        // ⚠️ Deux emplacements sont lus : le bloc « header » produit par
        // encode()/describe() et le format historique à plat. Sans cette
        // tolérance, l'aller-retour enregistrer → relire perdait le pied de
        // page et le texte institutionnel : l'interface les renvoyait dans
        // « header », le décodeur ne cherchait qu'à la racine, et le document
        // sortait sans eux alors que la version enregistrée les contenait.
        JsonNode header = root.get("header");
        boolean hasHeaderBlock = header != null && header.isObject();
        JsonNode headerSource = hasHeaderBlock ? header : root;

        DocumentStyle current = b.build();
        b.showLogo(bool(headerSource, "showLogo", current.showLogo));
        String logo = text(headerSource, "logoPosition", null);
        if (logo != null) {
            try {
                b.logoPosition(DocumentStyle.LogoPosition.valueOf(logo.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // position inconnue : on garde celle du modèle
            }
        }
        b.showSchoolName(bool(headerSource, "showSchoolName", current.showSchoolName));
        b.showContact(bool(headerSource, "showContact", current.showContact));
        b.showAddress(bool(headerSource, "showAddress", current.showAddress));
        b.headerRule(bool(headerSource, "headerRule", current.headerRule));
        b.footerText2(text(headerSource, "footerText", current.footerText2));
        b.institutionalText(text(headerSource, "institutionalText", current.institutionalText));
        String align = text(headerSource, "titleAlign", null);
        if (align != null) {
            try {
                b.titleAlign(DocumentStyle.TitleAlign.valueOf(align.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // alignement inconnu : on garde celui du modèle
            }
        }
        b.pageNumbers(bool(root, "pageNumbers", current.pageNumbers));
        b.zebraStripes(bool(root, "zebraStripes", current.zebraStripes));
        b.tableBorders(bool(root, "tableBorders", current.tableBorders));

        return b.build();
    }

    // ---------- Encodage ----------

    /** Sérialise un thème complet (formulaire → JSON stocké). */
    public static ObjectNode encode(DocumentStyle s) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("template", s.template.name());
        root.put("showLogo", s.showLogo);
        root.put("logoPosition", s.logoPosition.name());
        root.put("showSchoolName", s.showSchoolName);
        root.put("showContact", s.showContact);
        root.put("showAddress", s.showAddress);
        root.put("headerRule", s.headerRule);
        root.put("footerText", s.footerText2);
        root.put("institutionalText", s.institutionalText);
        root.put("titleAlign", s.titleAlign.name());
        root.put("pageFormat", s.pageFormat.name());
        root.put("pageNumbers", s.pageNumbers);
        root.put("zebraStripes", s.zebraStripes);
        root.put("tableBorders", s.tableBorders);

        ObjectNode colors = root.putObject("colors");
        colors.put("primary", DocumentThemePresets.toHex(s.primary));
        colors.put("secondary", DocumentThemePresets.toHex(s.secondary));
        colors.put("accent", DocumentThemePresets.toHex(s.accent));
        colors.put("heading", DocumentThemePresets.toHex(s.heading));
        colors.put("tableHeader", DocumentThemePresets.toHex(s.tableHeader));
        colors.put("headerText", DocumentThemePresets.toHex(s.headerText));
        colors.put("border", DocumentThemePresets.toHex(s.border));
        colors.put("text", DocumentThemePresets.toHex(s.text));
        colors.put("sectionBackground", DocumentThemePresets.toHex(s.sectionBackground));
        colors.put("footerText", DocumentThemePresets.toHex(s.footerText));

        ObjectNode typo = root.putObject("typography");
        typo.put("fontFamily", s.fontFamily);
        typo.put("bodySize", s.bodySize);
        typo.put("titleSize", s.titleSize);
        typo.put("subtitleSize", s.subtitleSize);
        typo.put("tableSize", s.tableSize);

        ObjectNode layout = root.putObject("layout");
        layout.put("marginTop", s.marginTop);
        layout.put("marginRight", s.marginRight);
        layout.put("marginBottom", s.marginBottom);
        layout.put("marginLeft", s.marginLeft);
        layout.put("sectionSpacing", s.sectionSpacing);
        layout.put("orientation", s.orientation.name());
        return root;
    }

    /** Vue métier exposée au frontend (couleurs en HEX, tailles en points). */
    public static Map<String, Object> describe(DocumentStyle s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("template", s.template.name());
        m.put("templateLabel", s.template.label());
        Map<String, Object> colors = new LinkedHashMap<>();
        colors.put("primary", DocumentThemePresets.toHex(s.primary));
        colors.put("secondary", DocumentThemePresets.toHex(s.secondary));
        colors.put("accent", DocumentThemePresets.toHex(s.accent));
        colors.put("heading", DocumentThemePresets.toHex(s.heading));
        colors.put("tableHeader", DocumentThemePresets.toHex(s.tableHeader));
        colors.put("headerText", DocumentThemePresets.toHex(s.headerText));
        colors.put("border", DocumentThemePresets.toHex(s.border));
        colors.put("text", DocumentThemePresets.toHex(s.text));
        colors.put("sectionBackground", DocumentThemePresets.toHex(s.sectionBackground));
        colors.put("footerText", DocumentThemePresets.toHex(s.footerText));
        m.put("colors", colors);
        Map<String, Object> typo = new LinkedHashMap<>();
        typo.put("fontFamily", s.fontFamily);
        typo.put("bodySize", s.bodySize);
        typo.put("titleSize", s.titleSize);
        typo.put("subtitleSize", s.subtitleSize);
        typo.put("tableSize", s.tableSize);
        m.put("typography", typo);
        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("pageFormat", s.pageFormat.name());
        layout.put("orientation", s.orientation.name());
        layout.put("marginTop", s.marginTop);
        layout.put("marginRight", s.marginRight);
        layout.put("marginBottom", s.marginBottom);
        layout.put("marginLeft", s.marginLeft);
        layout.put("sectionSpacing", s.sectionSpacing);
        layout.put("pageNumbers", s.pageNumbers);
        layout.put("zebraStripes", s.zebraStripes);
        layout.put("tableBorders", s.tableBorders);
        m.put("layout", layout);
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("showLogo", s.showLogo);
        header.put("logoPosition", s.logoPosition.name());
        header.put("showSchoolName", s.showSchoolName);
        header.put("showContact", s.showContact);
        header.put("showAddress", s.showAddress);
        header.put("headerRule", s.headerRule);
        header.put("titleAlign", s.titleAlign.name());
        header.put("footerText", s.footerText2);
        header.put("institutionalText", s.institutionalText);
        m.put("header", header);
        return m;
    }

    // ---------- Validation ----------

    /**
     * Valide une configuration reçue du client.
     *
     * <p>Rejette (400) plutôt que de corriger : une couleur invalide
     * enregistrée silencieusement produirait un PDF différent de ce que
     * l'administrateur a vu en prévisualisation.</p>
     *
     * @throws BusinessException si une valeur est hors périmètre
     */
    public static void validate(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new BusinessException("Configuration de document invalide");
        }
        String template = optionalText(root, "template");
        if (template != null && !isTemplate(template)) {
            throw new BusinessException("Modèle de document inconnu : " + template);
        }
        String logo = optionalText(root, "logoPosition");
        if (logo != null && !in(logo, "LEFT", "CENTER", "RIGHT")) {
            throw new BusinessException("Position de logo invalide : " + logo);
        }
        String align = optionalText(root, "titleAlign");
        if (align != null && !in(align, "LEFT", "CENTER", "RIGHT")) {
            throw new BusinessException("Alignement de titre invalide : " + align);
        }
        assertTextLength(root, "footerText", 300);
        assertTextLength(root, "institutionalText", 300);

        JsonNode colors = root.get("colors");
        if (colors != null) {
            if (!colors.isObject()) {
                throw new BusinessException("Bloc « colors » invalide");
            }
            for (String field : COLOR_FIELDS) {
                String value = optionalText(colors, field);
                if (value == null) {
                    continue;
                }
                if (DocumentThemePresets.hex(value) == null) {
                    throw new BusinessException("Couleur invalide pour " + field
                            + " : « " + value + " » (format attendu #RRGGBB)");
                }
            }
        }

        JsonNode typo = root.get("typography");
        if (typo != null) {
            if (!typo.isObject()) {
                throw new BusinessException("Bloc « typography » invalide");
            }
            String font = optionalText(typo, "fontFamily");
            if (font != null && !isFontFamily(font)) {
                throw new BusinessException("Police non prise en charge : " + font
                        + " (valeurs acceptées : HELVETICA, TIMES, COURIER)");
            }
            assertRange(typo, "bodySize", 7f, 14f);
            assertRange(typo, "titleSize", 12f, 26f);
            assertRange(typo, "subtitleSize", 7f, 16f);
            assertRange(typo, "tableSize", 6f, 12f);
        }

        JsonNode layout = root.get("layout");
        if (layout != null) {
            if (!layout.isObject()) {
                throw new BusinessException("Bloc « layout » invalide");
            }
            String format = optionalText(layout, "pageFormat");
            if (format != null && !isPageFormat(format)) {
                throw new BusinessException("Format de page non pris en charge : " + format);
            }
            String orientation = optionalText(layout, "orientation");
            if (orientation != null && !in(orientation, "PORTRAIT", "LANDSCAPE")) {
                throw new BusinessException("Orientation invalide : " + orientation);
            }
            assertRange(layout, "marginTop", 14f, 90f);
            assertRange(layout, "marginRight", 14f, 90f);
            assertRange(layout, "marginBottom", 20f, 110f);
            assertRange(layout, "marginLeft", 14f, 90f);
            assertRange(layout, "sectionSpacing", 2f, 24f);
        }
    }

    /**
     * Contrôle le contraste perçu (WCAG, luminance relative) et renvoie les
     * avertissements — jamais un refus : l'administrateur reste maître de son
     * choix, mais il est prévenu qu'un texte pourrait devenir illisible.
     */
    public static java.util.List<String> contrastWarnings(DocumentStyle s) {
        java.util.List<String> warnings = new java.util.ArrayList<>();
        double headerContrast = contrast(s.headerText, s.tableHeader);
        if (headerContrast < 3.0) {
            warnings.add("Le texte des en-têtes de tableaux manque de contraste avec leur fond ("
                    + String.format(Locale.ROOT, "%.1f", headerContrast) + ":1, minimum conseillé 3:1).");
        }
        double bodyContrast = contrast(s.text, Color.WHITE);
        if (bodyContrast < 4.5) {
            warnings.add("La couleur du texte principal est trop claire sur fond blanc ("
                    + String.format(Locale.ROOT, "%.1f", bodyContrast) + ":1, minimum conseillé 4,5:1).");
        }
        double footerContrast = contrast(s.footerText, Color.WHITE);
        if (footerContrast < 3.0) {
            warnings.add("La couleur du pied de page est trop claire sur fond blanc ("
                    + String.format(Locale.ROOT, "%.1f", footerContrast) + ":1, minimum conseillé 3:1).");
        }
        return warnings;
    }

    /**
     * Luminance relative WCAG 2.1. Le contraste est aussi vérifié en niveaux
     * de gris (impression noir et blanc) : c'est ce ratio qui compte pour une
     * photocopie, pas la différence de teinte.
     */
    static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        double lighter = Math.max(la, lb);
        double darker = Math.min(la, lb);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(Color c) {
        double r = channel(c.getRed());
        double g = channel(c.getGreen());
        double bl = channel(c.getBlue());
        return 0.2126 * r + 0.7152 * g + 0.0722 * bl;
    }

    private static double channel(int value) {
        double v = value / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    // ---------- Utilitaires ----------

    public static boolean isTemplate(String value) {
        String v = value == null ? "" : value.toUpperCase(Locale.ROOT);
        return in(v, "CLASSIC", "MODERN", "PREMIUM", "MINIMAL", "CUSTOM");
    }

    public static boolean isFontFamily(String value) {
        return in(value == null ? "" : value.toUpperCase(Locale.ROOT), FONT_FAMILIES);
    }

    public static boolean isPageFormat(String value) {
        return in(value == null ? "" : value.toUpperCase(Locale.ROOT), PAGE_FORMATS);
    }

    private static boolean in(String value, String... allowed) {
        for (String a : allowed) {
            if (a.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static void assertRange(JsonNode node, String field, float min, float max) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return;
        }
        if (!v.isNumber()) {
            throw new BusinessException("Valeur numérique attendue pour " + field);
        }
        double d = v.asDouble();
        if (d < min || d > max) {
            throw new BusinessException("« " + field + " » doit être compris entre "
                    + (int) min + " et " + (int) max);
        }
    }

    private static void assertTextLength(JsonNode root, String field, int max) {
        String v = optionalText(root, field);
        if (v != null && v.length() > max) {
            throw new BusinessException("« " + field + " » ne peut pas dépasser " + max + " caractères");
        }
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isTextual()) {
            return null;
        }
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private static String text(JsonNode node, String field, String fallback) {
        String s = optionalText(node, field);
        return s == null ? fallback : s;
    }

    private static boolean bool(JsonNode node, String field, boolean fallback) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? fallback : v.asBoolean(fallback);
    }

    private static float num(JsonNode node, String field, float fallback) {
        JsonNode v = node.get(field);
        return v == null || !v.isNumber() ? fallback : (float) v.asDouble();
    }

    private static void applyColor(JsonNode colors, String field, DocumentStyle.Builder b) {
        Color c = DocumentThemePresets.hex(optionalText(colors, field));
        if (c == null) {
            return;
        }
        switch (field) {
            case "primary" -> b.primary(c);
            case "secondary" -> b.secondary(c);
            case "accent" -> b.accent(c);
            case "heading" -> b.heading(c);
            case "tableHeader" -> b.tableHeader(c);
            case "headerText" -> b.headerText(c);
            case "border" -> b.border(c);
            case "text" -> b.text(c);
            case "sectionBackground" -> b.sectionBackground(c);
            case "footerText" -> b.footerText(c);
            default -> {
                // champ inconnu : ignoré
            }
        }
    }

    /** Parseur JSON partagé (lecture de {@code config_json}). */
    public static JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    /** Sérialise un nœud en chaîne compacte. */
    public static String stringify(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return "{}";
        }
    }
}
