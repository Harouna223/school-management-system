package com.school.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.school.exception.BusinessException;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lecture, écriture et validation de la configuration de la carte scolaire.
 *
 * <p>Même principe que {@link DocumentStyleCodec} : le JSON stocké est la seule
 * source de vérité, chaque champ est typé et borné, et une valeur invalide est
 * <b>refusée à l'enregistrement</b> plutôt que corrigée silencieusement à la
 * génération. Un administrateur qui choisit « #zzz » comme couleur de bordure
 * doit voir un message, pas une carte qui sort en noir sans explication.</p>
 *
 * <p>À la lecture, aucune exception n'est levée : une configuration absente ou
 * corrompue retombe sur {@link CardConfig#defaults()}. Un document doit
 * toujours pouvoir être produit, même si la base contient une valeur
 * inattendue.</p>
 */
public final class CardConfigCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CardConfigCodec() {
    }

    /** Bornes de validation, alignées sur celles du {@code Builder}. */
    public static final Map<String, float[]> RANGES = ranges();

    private static Map<String, float[]> ranges() {
        Map<String, float[]> m = new LinkedHashMap<>();
        m.put("borderWidth", new float[]{0.3f, 2f});
        m.put("emblemSize", new float[]{14f, 30f});
        m.put("titleSize", new float[]{6f, 11f});
        m.put("schoolNameSize", new float[]{4.5f, 9f});
        m.put("schoolLogoSize", new float[]{10f, 24f});
        return java.util.Collections.unmodifiableMap(m);
    }

    /** Longueur maximale des textes libres : au-delà, le texte déborde du cadre. */
    private static final int MAX_TITLE = 60;
    private static final int MAX_HEADER_LINE = 80;
    private static final int MAX_FOOTER = 80;

    // ==================================================================
    // Décodage
    // ==================================================================

    /** Construit une configuration depuis un JSON ; {@code null} → valeurs d'origine. */
    public static CardConfig decode(JsonNode root) {
        CardConfig.Builder b = CardConfig.builder();
        if (root == null || root.isNull() || !root.isObject()) {
            return b.build();
        }

        b.showBorder(bool(root, "showBorder", true));
        b.borderColor(color(root, "borderColor", Color.BLACK));
        b.borderWidth((float) num(root, "borderWidth", 0.9));

        b.emblemSource(emblemSource(text(root, "emblemSource", null)));
        b.emblemSize((float) num(root, "emblemSize", 22));

        b.showFlag(bool(root, "showFlag", true));

        b.showHeader(bool(root, "showHeader", true));
        // Un tableau d'une autre longueur est ignoré : on garde l'en-tête
        // d'origine plutôt que d'inventer un en-tête bancal.
        JsonNode lines = root.get("headerLines");
        if (lines != null && lines.isArray() && lines.size() == 3) {
            b.headerLine1(lines.get(0).asText(""));
            b.headerLine2(lines.get(1).asText(""));
            b.headerLine3(lines.get(2).asText(""));
        }

        b.title(text(root, "title", "CARTE D'IDENTITÉ SCOLAIRE"));
        b.titleSize((float) num(root, "titleSize", 8.5));

        b.showBand(bool(root, "showBand", true));
        JsonNode band = root.get("bandColors");
        if (band != null && band.isObject()) {
            b.bandGreen(color(band, "green", new Color(20, 181, 58)));
            b.bandYellow(color(band, "yellow", new Color(252, 209, 22)));
            b.bandRed(color(band, "red", new Color(206, 17, 38)));
        }

        b.showSchoolName(bool(root, "showSchoolName", true));
        b.schoolNameSize((float) num(root, "schoolNameSize", 6.5));

        b.showSchoolLogo(bool(root, "showSchoolLogo", false));
        b.schoolLogoSize((float) num(root, "schoolLogoSize", 15));

        b.showPhotoFrame(bool(root, "showPhotoFrame", true));
        b.photoFrameColor(color(root, "photoFrameColor", new Color(15, 42, 78)));

        List<CardConfig.Field> fields = new ArrayList<>();
        JsonNode arr = root.get("fields");
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                CardConfig.Field f = field(n.asText(""));
                if (f != null && !fields.contains(f)) {
                    fields.add(f);
                }
            }
        }
        b.fields(fields);

        b.footerText(text(root, "footerText", "Signature et cachet du Directeur"));

        b.headingColor(color(root, "headingColor", new Color(15, 42, 78)));
        b.textColor(color(root, "textColor", new Color(40, 48, 58)));

        return b.build();
    }

    // ==================================================================
    // Encodage
    // ==================================================================

    /** Sérialise une configuration complète (stockage en base et réponse API). */
    public static ObjectNode encode(CardConfig c) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("showBorder", c.showBorder);
        root.put("borderColor", DocumentThemePresets.toHex(c.borderColor));
        root.put("borderWidth", round(c.borderWidth));
        root.put("emblemSource", c.emblemSource.name());
        root.put("emblemSize", round(c.emblemSize));
        root.put("showFlag", c.showFlag);
        root.put("showHeader", c.showHeader);
        ArrayNode lines = root.putArray("headerLines");
        lines.add(c.headerLine1);
        lines.add(c.headerLine2);
        lines.add(c.headerLine3);
        root.put("title", c.title);
        root.put("titleSize", round(c.titleSize));
        root.put("showBand", c.showBand);
        ObjectNode band = root.putObject("bandColors");
        band.put("green", DocumentThemePresets.toHex(c.bandGreen));
        band.put("yellow", DocumentThemePresets.toHex(c.bandYellow));
        band.put("red", DocumentThemePresets.toHex(c.bandRed));
        root.put("showSchoolName", c.showSchoolName);
        root.put("schoolNameSize", round(c.schoolNameSize));
        root.put("showSchoolLogo", c.showSchoolLogo);
        root.put("schoolLogoSize", round(c.schoolLogoSize));
        root.put("showPhotoFrame", c.showPhotoFrame);
        root.put("photoFrameColor", DocumentThemePresets.toHex(c.photoFrameColor));
        ArrayNode fields = root.putArray("fields");
        c.fields.forEach(f -> fields.add(f.name()));
        root.put("footerText", c.footerText);
        root.put("headingColor", DocumentThemePresets.toHex(c.headingColor));
        root.put("textColor", DocumentThemePresets.toHex(c.textColor));
        return root;
    }

    /**
     * Vue exposée au client : la configuration, plus le catalogue des valeurs
     * possibles. Le frontend n'a ainsi aucune liste de champs à maintenir en
     * double — ajouter un {@link CardConfig.Field} côté serveur le fait
     * apparaître dans l'écran de réglage.
     */
    public static Map<String, Object> describe(CardConfig c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("config", encode(c));
        m.put("emblemSources", java.util.Arrays.stream(CardConfig.EmblemSource.values())
                .map(e -> Map.of("code", e.name(), "label", e.label()))
                .toList());
        m.put("availableFields", java.util.Arrays.stream(CardConfig.Field.values())
                .map(f -> Map.of("code", f.name(), "label", f.label()))
                .toList());
        m.put("maxFields", CardConfig.MAX_FIELDS);
        return m;
    }

    // ==================================================================
    // Validation
    // ==================================================================

    /**
     * Valide une configuration reçue du client.
     *
     * @throws BusinessException si une valeur sort du périmètre réalisable
     */
    public static void validate(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new BusinessException("Configuration de la carte scolaire invalide");
        }

        String source = text(root, "emblemSource", null);
        if (source != null && emblemSource(source) == null) {
            throw new BusinessException("Provenance d'emblème inconnue : " + source
                    + " (valeurs acceptées : NONE, EMBLEM, SCHOOL_LOGO)");
        }

        for (String field : new String[]{"borderColor", "photoFrameColor", "headingColor", "textColor"}) {
            assertColor(root, field);
        }

        JsonNode band = root.get("bandColors");
        if (band != null) {
            if (!band.isObject()) {
                throw new BusinessException("Bloc « bandColors » invalide");
            }
            assertColor(band, "green");
            assertColor(band, "yellow");
            assertColor(band, "red");
        }

        for (Map.Entry<String, float[]> e : RANGES.entrySet()) {
            assertRange(root, e.getKey(), e.getValue()[0], e.getValue()[1]);
        }

        assertLength(root, "title", MAX_TITLE);
        assertLength(root, "footerText", MAX_FOOTER);

        JsonNode lines = root.get("headerLines");
        if (lines != null) {
            if (!lines.isArray() || lines.size() != 3) {
                throw new BusinessException("L'en-tête doit comporter exactement trois lignes");
            }
            for (int i = 0; i < 3; i++) {
                if (!lines.get(i).isTextual()) {
                    throw new BusinessException("Ligne d'en-tête " + (i + 1) + " invalide");
                }
                if (lines.get(i).asText().length() > MAX_HEADER_LINE) {
                    throw new BusinessException("La ligne d'en-tête " + (i + 1)
                            + " ne peut pas dépasser " + MAX_HEADER_LINE + " caractères");
                }
            }
        }

        JsonNode fields = root.get("fields");
        if (fields != null) {
            if (!fields.isArray()) {
                throw new BusinessException("Bloc « fields » invalide");
            }
            if (fields.isEmpty()) {
                throw new BusinessException("Au moins un champ d'identité doit être imprimé");
            }
            if (fields.size() > CardConfig.MAX_FIELDS) {
                throw new BusinessException("Au plus " + CardConfig.MAX_FIELDS
                        + " champs peuvent être imprimés sur la carte");
            }
            java.util.Set<CardConfig.Field> seen = new java.util.LinkedHashSet<>();
            for (JsonNode n : fields) {
                CardConfig.Field f = field(n.asText(""));
                if (f == null) {
                    throw new BusinessException("Champ d'identité inconnu : " + n.asText(""));
                }
                if (!seen.add(f)) {
                    throw new BusinessException("Champ d'identité en double : " + f.name());
                }
            }
        }
    }

    // ==================================================================
    // Utilitaires
    // ==================================================================

    /** Champ d'identité depuis son code, {@code null} si inconnu. */
    public static CardConfig.Field field(String code) {
        if (code == null) {
            return null;
        }
        try {
            return CardConfig.Field.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Provenance d'emblème depuis son code, {@code null} si inconnue. */
    public static CardConfig.EmblemSource emblemSource(String code) {
        if (code == null) {
            return null;
        }
        try {
            return CardConfig.EmblemSource.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void assertColor(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return;
        }
        if (!v.isTextual()) {
            throw new BusinessException("Couleur attendue en texte pour " + field);
        }
        if (DocumentThemePresets.hex(v.asText()) == null) {
            throw new BusinessException("Couleur invalide pour " + field
                    + " : « " + v.asText() + " » (format attendu #RRGGBB)");
        }
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
                    + trim(min) + " et " + trim(max));
        }
    }

    private static String trim(float v) {
        return v == Math.rint(v) ? String.valueOf((int) v)
                : String.format(Locale.ROOT, "%.1f", v);
    }

    private static void assertLength(JsonNode root, String field, int max) {
        JsonNode v = root.get(field);
        if (v == null || v.isNull() || !v.isTextual()) {
            return;
        }
        if (v.asText().length() > max) {
            throw new BusinessException("« " + field + " » ne peut pas dépasser " + max + " caractères");
        }
    }

    private static Color color(JsonNode node, String field, Color fallback) {
        JsonNode v = node.get(field);
        if (v == null || !v.isTextual()) {
            return fallback;
        }
        Color c = DocumentThemePresets.hex(v.asText());
        return c == null ? fallback : c;
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isTextual()) {
            return fallback;
        }
        return v.asText();
    }

    private static boolean bool(JsonNode node, String field, boolean fallback) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? fallback : v.asBoolean(fallback);
    }

    private static double num(JsonNode node, String field, double fallback) {
        JsonNode v = node.get(field);
        return v == null || !v.isNumber() ? fallback : v.asDouble();
    }

    private static double round(float v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Parseur JSON partagé (lecture de la clé {@code SCHOOL_CARD_CONFIG}). */
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
