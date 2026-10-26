package com.school.pdf;

import java.awt.Color;
import java.util.List;

/**
 * Configuration graphique de la <b>carte scolaire</b> (format ID-1).
 *
 * <p>La carte est le seul document dont la mise en page est dessinée à la main
 * au {@code PdfContentByte} : elle ne passe pas par le moteur d'en-tête/tableaux
 * de {@link SchoolDocumentTheme}. Elle avait donc jusqu'ici une apparence
 * entièrement codée en dur. Cette classe décrit ce qui est <b>réellement
 * modifiable sans rendre la carte illisible</b> :</p>
 *
 * <ul>
 *   <li>la présence et la couleur de chaque élément (bordure, emblème, drapeau,
 *       en-tête institutionnel, bande tricolore, nom de l'établissement, logo,
 *       cadre photo, pied de carte) ;</li>
 *   <li>les <b>textes</b> de l'en-tête, du titre et du pied ;</li>
 *   <li>les <b>couleurs</b> du titre, des libellés et des valeurs ;</li>
 *   <li>la <b>liste des champs</b> d'identité imprimés, dans l'ordre canonique.</li>
 * </ul>
 *
 * <p><b>Ce qui reste volontairement figé</b> : le format (85,6 × 54 mm, soit
 * 242,65 × 153,07 pt), la position des blocs et l'espacement des lignes. Une
 * carte d'identité scolaire doit rester au format CR80 pour tenir dans une
 * pochette et être lue par un lecteur de badge ; laisser déplacer librement les
 * blocs produirait des cartes imprimables mais inutilisables. Les tailles de
 * police sont donc bornées, pas libres.</p>
 *
 * <p><b>Invariant</b> : {@link #defaults()} reproduit exactement le rendu
 * historique. Tant que l'administrateur n'a rien enregistré, la carte sort
 * identique à avant au point près.</p>
 */
public final class CardConfig {

    /** Provenance de l'image affichée dans la pastille en haut à gauche. */
    public enum EmblemSource {
        /** Aucune image : la place est laissée vide. */
        NONE("Aucun"),
        /** Emblème national téléversé dans la rubrique « Carte scolaire ». */
        EMBLEM("Emblème de l'établissement"),
        /** Logo de l'établissement, saisi dans « Informations de l'établissement ». */
        SCHOOL_LOGO("Logo de l'école");

        private final String label;

        EmblemSource(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * Champs d'identité imprimables, dans l'ordre d'affichage.
     *
     * <p>L'ordre est celui du code : il n'est pas modifiable depuis l'interface,
     * car les sept lignes occupent une zone de hauteur fixe. L'administrateur
     * choisit <b>lesquels</b> apparaissent, pas leur ordre — un ordre libre
     * permettrait de faire déborder la septième ligne sur le pied de carte.</p>
     */
    public enum Field {
        LAST_NAME("Nom"),
        FIRST_NAME("Prénom(s)"),
        MATRICULE("Matricule"),
        BIRTH_DATE("Date de naissance"),
        BIRTH_PLACE("Lieu de naissance"),
        GENDER("Sexe"),
        CLASS_NAME("Classe");

        private final String label;

        Field(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Nombre maximal de champs imprimables (hauteur disponible / interligne). */
    public static final int MAX_FIELDS = 7;

    // ---------- Apparence (immuable) ----------

    public final boolean showBorder;
    public final Color borderColor;
    public final float borderWidth;

    public final EmblemSource emblemSource;
    public final float emblemSize;

    public final boolean showFlag;

    public final boolean showHeader;
    public final String headerLine1;
    public final String headerLine2;
    public final String headerLine3;

    public final String title;
    public final float titleSize;

    public final boolean showBand;
    public final Color bandGreen;
    public final Color bandYellow;
    public final Color bandRed;

    public final boolean showSchoolName;
    public final float schoolNameSize;

    public final boolean showSchoolLogo;
    public final float schoolLogoSize;

    public final boolean showPhotoFrame;
    public final Color photoFrameColor;

    public final List<Field> fields;
    public final String footerText;

    public final Color headingColor;
    public final Color textColor;

    private CardConfig(Builder b) {
        this.showBorder = b.showBorder;
        this.borderColor = b.borderColor;
        this.borderWidth = b.borderWidth;
        this.emblemSource = b.emblemSource;
        this.emblemSize = b.emblemSize;
        this.showFlag = b.showFlag;
        this.showHeader = b.showHeader;
        this.headerLine1 = b.headerLine1;
        this.headerLine2 = b.headerLine2;
        this.headerLine3 = b.headerLine3;
        this.title = b.title;
        this.titleSize = b.titleSize;
        this.showBand = b.showBand;
        this.bandGreen = b.bandGreen;
        this.bandYellow = b.bandYellow;
        this.bandRed = b.bandRed;
        this.showSchoolName = b.showSchoolName;
        this.schoolNameSize = b.schoolNameSize;
        this.showSchoolLogo = b.showSchoolLogo;
        this.schoolLogoSize = b.schoolLogoSize;
        this.showPhotoFrame = b.showPhotoFrame;
        this.photoFrameColor = b.photoFrameColor;
        this.fields = List.copyOf(b.fields);
        this.footerText = b.footerText;
        this.headingColor = b.headingColor;
        this.textColor = b.textColor;
    }

    /** Rendu historique de la carte, au point près. */
    public static CardConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.showBorder = showBorder;
        b.borderColor = borderColor;
        b.borderWidth = borderWidth;
        b.emblemSource = emblemSource;
        b.emblemSize = emblemSize;
        b.showFlag = showFlag;
        b.showHeader = showHeader;
        b.headerLine1 = headerLine1;
        b.headerLine2 = headerLine2;
        b.headerLine3 = headerLine3;
        b.title = title;
        b.titleSize = titleSize;
        b.showBand = showBand;
        b.bandGreen = bandGreen;
        b.bandYellow = bandYellow;
        b.bandRed = bandRed;
        b.showSchoolName = showSchoolName;
        b.schoolNameSize = schoolNameSize;
        b.showSchoolLogo = showSchoolLogo;
        b.schoolLogoSize = schoolLogoSize;
        b.showPhotoFrame = showPhotoFrame;
        b.photoFrameColor = photoFrameColor;
        b.fields = fields;
        b.footerText = footerText;
        b.headingColor = headingColor;
        b.textColor = textColor;
        return b;
    }

    /** Vrai si au moins un champ d'identité est imprimé. */
    public boolean printsAnyField() {
        return !fields.isEmpty();
    }

    // ---------- Construction ----------

    public static final class Builder {

        private boolean showBorder = true;
        private Color borderColor = Color.BLACK;
        private float borderWidth = 0.9f;

        // Le rendu historique utilise le logo de l'établissement dans la
        // pastille : c'est la valeur par défaut, pas un choix arbitraire.
        private EmblemSource emblemSource = EmblemSource.SCHOOL_LOGO;
        private float emblemSize = 22f;

        private boolean showFlag = true;

        private boolean showHeader = true;
        private String headerLine1 = "RÉPUBLIQUE DU MALI";
        private String headerLine2 = "Un Peuple – Un But – Une Foi";
        private String headerLine3 = "Ministère de l'Éducation Nationale";

        private String title = "CARTE D'IDENTITÉ SCOLAIRE";
        private float titleSize = 8.5f;

        private boolean showBand = true;
        private Color bandGreen = new Color(20, 181, 58);
        private Color bandYellow = new Color(252, 209, 22);
        private Color bandRed = new Color(206, 17, 38);

        private boolean showSchoolName = true;
        private float schoolNameSize = 6.5f;

        private boolean showSchoolLogo = false;
        private float schoolLogoSize = 15f;

        private boolean showPhotoFrame = true;
        private Color photoFrameColor = new Color(15, 42, 78);

        private List<Field> fields = List.of(Field.values());
        private String footerText = "Signature et cachet du Directeur";

        private Color headingColor = new Color(15, 42, 78);
        private Color textColor = new Color(40, 48, 58);

        public Builder showBorder(boolean v) { this.showBorder = v; return this; }
        public Builder borderColor(Color v) { this.borderColor = nz(v, Color.BLACK); return this; }
        public Builder borderWidth(float v) { this.borderWidth = clamp(v, 0.3f, 2f, 0.9f); return this; }

        public Builder emblemSource(EmblemSource v) {
            this.emblemSource = v == null ? EmblemSource.SCHOOL_LOGO : v;
            return this;
        }
        public Builder emblemSize(float v) { this.emblemSize = clamp(v, 14f, 30f, 22f); return this; }

        public Builder showFlag(boolean v) { this.showFlag = v; return this; }

        public Builder showHeader(boolean v) { this.showHeader = v; return this; }
        public Builder headerLine1(String v) { this.headerLine1 = text(v, "RÉPUBLIQUE DU MALI"); return this; }
        public Builder headerLine2(String v) { this.headerLine2 = text(v, "Un Peuple – Un But – Une Foi"); return this; }
        public Builder headerLine3(String v) {
            this.headerLine3 = text(v, "Ministère de l'Éducation Nationale");
            return this;
        }

        public Builder title(String v) { this.title = text(v, "CARTE D'IDENTITÉ SCOLAIRE"); return this; }
        public Builder titleSize(float v) { this.titleSize = clamp(v, 6f, 11f, 8.5f); return this; }

        public Builder showBand(boolean v) { this.showBand = v; return this; }
        public Builder bandGreen(Color v) { this.bandGreen = nz(v, new Color(20, 181, 58)); return this; }
        public Builder bandYellow(Color v) { this.bandYellow = nz(v, new Color(252, 209, 22)); return this; }
        public Builder bandRed(Color v) { this.bandRed = nz(v, new Color(206, 17, 38)); return this; }

        public Builder showSchoolName(boolean v) { this.showSchoolName = v; return this; }
        public Builder schoolNameSize(float v) { this.schoolNameSize = clamp(v, 4.5f, 9f, 6.5f); return this; }

        public Builder showSchoolLogo(boolean v) { this.showSchoolLogo = v; return this; }
        public Builder schoolLogoSize(float v) { this.schoolLogoSize = clamp(v, 10f, 24f, 15f); return this; }

        public Builder showPhotoFrame(boolean v) { this.showPhotoFrame = v; return this; }
        public Builder photoFrameColor(Color v) { this.photoFrameColor = nz(v, new Color(15, 42, 78)); return this; }

        /**
         * Champs imprimés. Une liste vide ou plus longue que {@link #MAX_FIELDS}
         * est ramenée dans les bornes : jamais zéro champ (une carte sans
         * identité n'identifie personne) et jamais plus que la place disponible.
         */
        public Builder fields(List<Field> v) {
            if (v == null || v.isEmpty()) {
                this.fields = List.of(Field.values());
                return this;
            }
            java.util.List<Field> kept = new java.util.ArrayList<>();
            for (Field f : Field.values()) {
                if (v.contains(f)) {
                    kept.add(f);
                }
            }
            if (kept.isEmpty()) {
                this.fields = List.of(Field.values());
                return this;
            }
            this.fields = kept.size() > MAX_FIELDS ? kept.subList(0, MAX_FIELDS) : kept;
            return this;
        }

        public Builder footerText(String v) {
            this.footerText = text(v, "Signature et cachet du Directeur");
            return this;
        }

        public Builder headingColor(Color v) { this.headingColor = nz(v, new Color(15, 42, 78)); return this; }
        public Builder textColor(Color v) { this.textColor = nz(v, new Color(40, 48, 58)); return this; }

        public CardConfig build() {
            return new CardConfig(this);
        }

        private static Color nz(Color v, Color fallback) {
            return v == null ? fallback : v;
        }

        private static String text(String v, String fallback) {
            if (v == null) {
                return fallback;
            }
            String t = v.trim();
            return t.isEmpty() ? "" : t;
        }

        private static float clamp(float v, float min, float max, float fallback) {
            if (Float.isNaN(v)) {
                return fallback;
            }
            return Math.max(min, Math.min(max, v));
        }
    }
}
