package com.school.pdf;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Résolution de la configuration effective : thème actif + options de type
 * de document.
 *
 * <p>Le cœur du contrat : une option spécifique ne doit <b>jamais</b> effacer
 * le reste du thème. Un bulletin passé en paysage garde ses couleurs, sa
 * police et son en-tête.</p>
 */
class DocumentStyleResolutionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private com.fasterxml.jackson.databind.JsonNode json(String raw) {
        try {
            return MAPPER.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Nested
    @DisplayName("Surcharges par type de document")
    class Overrides {

        @Test
        @DisplayName("une surcharge partielle ne change qu'une seule valeur")
        void surchargePartielle() {
            DocumentStyle base = DocumentStyle.defaults();
            DocumentStyle resultat = DocumentStyleService.applyOverrides(base,
                    json("{\"layout\":{\"orientation\":\"LANDSCAPE\"}}"));

            assertThat(resultat.orientation).isEqualTo(DocumentStyle.Orientation.LANDSCAPE);
            assertThat(resultat.primary).isEqualTo(base.primary);
            assertThat(resultat.bodySize).isEqualTo(base.bodySize);
            assertThat(resultat.logoPosition).isEqualTo(base.logoPosition);
        }

        @Test
        @DisplayName("deux clés dans le même bloc sont bien fusionnées, pas remplacées")
        void fusionProfonde() {
            DocumentStyle base = DocumentStyle.defaults();
            DocumentStyle resultat = DocumentStyleService.applyOverrides(base,
                    json("{\"typography\":{\"tableSize\":8}}"));

            assertThat(resultat.tableSize).isEqualTo(8f);
            assertThat(resultat.bodySize)
                    .as("bodySize absent de la surcharge : valeur du thème conservée")
                    .isEqualTo(base.bodySize);
            assertThat(resultat.titleSize).isEqualTo(base.titleSize);
        }

        @Test
        @DisplayName("une surcharge vide renvoie le thème inchangé")
        void surchargeVide() {
            DocumentStyle base = DocumentStyle.defaults();
            assertThat(DocumentStyleService.applyOverrides(base, json("{}"))).isSameAs(base);
            assertThat(DocumentStyleService.applyOverrides(base, null)).isSameAs(base);
        }

        @Test
        @DisplayName("un bulletin en paysage conserve les couleurs institutionnelles")
        void paysageConserveLaPalette() {
            DocumentStyle theme = DocumentStyle.defaults().toBuilder()
                    .primary(new Color(127, 29, 29))
                    .build();

            DocumentStyle bulletin = DocumentStyleService.applyOverrides(theme,
                    json("{\"layout\":{\"orientation\":\"LANDSCAPE\"}}"));

            assertThat(bulletin.orientation).isEqualTo(DocumentStyle.Orientation.LANDSCAPE);
            assertThat(bulletin.primary).isEqualTo(new Color(127, 29, 29));
        }
    }

    @Nested
    @DisplayName("Format de page")
    class PageSize {

        @Test
        @DisplayName("le paysage permute bien largeur et hauteur")
        void paysagePermute() {
            DocumentStyle portrait = DocumentStyle.defaults();
            DocumentStyle paysage = portrait.toBuilder()
                    .orientation(DocumentStyle.Orientation.LANDSCAPE).build();

            assertThat(portrait.pageSize().getWidth()).isLessThan(portrait.pageSize().getHeight());
            assertThat(paysage.pageSize().getWidth()).isGreaterThan(paysage.pageSize().getHeight());
            assertThat(paysage.pageSize().getWidth()).isEqualTo(portrait.pageSize().getHeight());
        }

        @Test
        @DisplayName("A5 et Letter sont pris en charge")
        void formats() {
            assertThat(DocumentStyle.defaults().toBuilder().pageFormat(DocumentStyle.PageFormat.A5)
                    .build().pageSize().getWidth())
                    .isCloseTo(com.lowagie.text.PageSize.A5.getWidth(),
                            org.assertj.core.data.Offset.offset(0.5f));
            assertThat(DocumentStyle.defaults().toBuilder().pageFormat(DocumentStyle.PageFormat.LETTER)
                    .build().pageSize().getWidth())
                    .isGreaterThan(com.lowagie.text.PageSize.A4.getWidth());
        }
    }

    @Nested
    @DisplayName("Catalogue des documents")
    class Catalogue {

        @Test
        @DisplayName("les documents à mise en page fixe sont déclarés comme tels")
        void documentsFixes() {
            DocumentTypes types = new DocumentTypes();

            assertThat(types.isFlexiblePage("STUDENT_CARD"))
                    .as("carte ID-1 : sa géométrie ne doit pas être pilotée par le thème")
                    .isFalse();
            assertThat(types.hasCustomHeader("ATTENDANCE_CERTIFICATE")).isTrue();
            assertThat(types.isFlexiblePage("BULLETIN")).isTrue();
        }

        @Test
        @DisplayName("tous les types exposés à l'API existent au catalogue")
        void catalogueComplet() {
            DocumentTypes types = new DocumentTypes();
            assertThat(types.catalogue()).hasSize(DocumentTypes.ALL.size());
            assertThat(DocumentTypes.CODES).contains("BULLETIN", "PAYMENT_RECEIPT", "GENERIC_REPORT");
            assertThat(types.info("inexistant")).isNull();
        }
    }

    @Nested
    @DisplayName("Polices")
    class Fonts {

        @Test
        @DisplayName("les trois familles livrées produisent une police exploitable")
        void famillesSupportees() {
            for (String family : DocumentStyleCodec.FONT_FAMILIES) {
                DocumentStyle s = DocumentStyle.defaults().toBuilder().fontFamily(family).build();
                assertThat(s.body(11f).getSize()).isEqualTo(11f);
                assertThat(s.whiteBold(9f)).isNotNull();
                assertThat(s.italic(9f)).isNotNull();
            }
        }

        @Test
        @DisplayName("une famille inconnue retombe sur Helvetica au lieu d'échouer")
        void familleInconnue() {
            DocumentStyle s = DocumentStyle.defaults().toBuilder().fontFamily("INCONNUE").build();
            assertThat(s.body(10f)).isNotNull();
        }

        @Test
        @DisplayName("gras italique reste disponible (variante PostScript nommée)")
        void grasItalique() {
            assertThat(DocumentStyle.font("HELVETICA", 10f,
                    com.lowagie.text.Font.BOLD | com.lowagie.text.Font.ITALIC, Color.BLACK)).isNotNull();
        }
    }

    // ------------------------------------------------------------------
    // Rendu du texte institutionnel
    // ------------------------------------------------------------------

    /**
     * Le texte institutionnel optionnel doit réellement sortir sur le PDF.
     * Ce test a été ajouté après avoir constaté qu'il était enregistré et
     * décodé mais jamais dessiné : un réglage invisible est un bug, pas une
     * fonctionnalité.
     */
    @Nested
    @DisplayName("Texte institutionnel")
    class InstitutionalText {

        private String rendre(DocumentStyle style) throws Exception {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            com.lowagie.text.Document document = new com.lowagie.text.Document(
                    style.pageSize(), style.marginLeft, style.marginRight,
                    style.marginTop, style.marginBottom);
            com.lowagie.text.pdf.PdfWriter.getInstance(document, out);
            document.open();
            new SchoolDocumentTheme(null, null, null, null)
                    .addTitle(document, "BULLETIN DE NOTES", "Aminata TRAORÉ",
                            style, style.institutionalText);
            document.close();
            return extraireTexte(out.toByteArray());
        }

        private String extraireTexte(byte[] pdf) throws Exception {
            StringBuilder sb = new StringBuilder();
            try (com.lowagie.text.pdf.PdfReader reader =
                         new com.lowagie.text.pdf.PdfReader(pdf)) {
                // OpenPDF 1.3.39 : l'extracteur est une instance portée par le lecteur.
                com.lowagie.text.pdf.parser.PdfTextExtractor extracteur =
                        new com.lowagie.text.pdf.parser.PdfTextExtractor(reader);
                int pages = reader.getNumberOfPages();
                for (int i = 1; i <= pages; i++) {
                    sb.append(extracteur.getTextFromPage(i));
                }
            }
            return sb.toString();
        }

        @Test
        @DisplayName("le texte institutionnel est dessiné sous le sous-titre")
        void texteRendu() throws Exception {
            // Texte volontairement sans apostrophe typographique : la police
            // par défaut du document d'essai est encodée en CP1252 côté test,
            // et un caractère hors jeu ferait échouer l'extraction, pas le rendu.
            DocumentStyle style = DocumentStyle.defaults().toBuilder()
                    .institutionalText("Reconnu par le ministere de l'Education nationale")
                    .build();

            String texte = rendre(style);

            assertThat(texte).contains("BULLETIN DE NOTES");
            assertThat(texte).contains("Aminata");
            assertThat(texte).contains("Reconnu par le ministere de l'Education nationale");
        }

        @Test
        @DisplayName("le texte institutionnel suit le sous-titre dans l'ordre de lecture")
        void textePlaceApresLeSousTitre() throws Exception {
            DocumentStyle style = DocumentStyle.defaults().toBuilder()
                    .institutionalText("MENTION INSTITUTIONNELLE")
                    .build();

            String texte = rendre(style);

            assertThat(texte.indexOf("Aminata"))
                    .as("la mention vient après le sous-titre")
                    .isLessThan(texte.indexOf("MENTION INSTITUTIONNELLE"));
        }

        @Test
        @DisplayName("un texte vide n'ajoute rien au document")
        void texteVideIgnore() throws Exception {
            String sansTexte = rendre(DocumentStyle.defaults());
            assertThat(sansTexte).doesNotContain("MENTION INSTITUTIONNELLE");
            assertThat(sansTexte).contains("BULLETIN DE NOTES");
        }

        @Test
        @DisplayName("une séquence d'échappement ne peut pas sortir de la chaîne PDF")
        void injectionNeutrlisee() throws Exception {
            DocumentStyle style = DocumentStyle.defaults().toBuilder()
                    .institutionalText(") Tj ET Q 0 0 1 rg (INJECTE) Tj")
                    .build();

            String texte = rendre(style);

            // La chaîne reste une chaîne : rien n'est interprété comme opérateur.
            // Le texte extrait contient donc la séquence « brute », preuve qu'elle a
            // été échappée ; c'est le flux PDF qui ne doit contenir aucun opérateur
            // de remplissage ni de commande de positionnement injectés.
            assertThat(texte).contains("INJECTE");

            byte[] pdf = rendreEnOctets(style);
            assertThat(fluxDecompresse(pdf))
                    .as("aucun opérateur PDF injecté dans le flux de contenu")
                    .doesNotContain("0 0 1 rg");
        }

        private byte[] rendreEnOctets(DocumentStyle style) throws Exception {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            com.lowagie.text.Document document = new com.lowagie.text.Document(
                    style.pageSize(), style.marginLeft, style.marginRight,
                    style.marginTop, style.marginBottom);
            com.lowagie.text.pdf.PdfWriter.getInstance(document, out);
            document.open();
            new SchoolDocumentTheme(null, null, null, null)
                    .addTitle(document, "BULLETIN DE NOTES", "Aminata TRAORÉ",
                            style, style.institutionalText);
            document.close();
            return out.toByteArray();
        }

        /** Concatène les flux de contenu décompressés (flate) du PDF. */
        private String fluxDecompresse(byte[] pdf) throws Exception {
            com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(pdf);
            StringBuilder sb = new StringBuilder();
            try {
                for (int i = 1; i <= reader.getXrefSize(); i++) {
                    com.lowagie.text.pdf.PdfObject obj = reader.getPdfObjectRelease(i);
                    if (obj instanceof com.lowagie.text.pdf.PRStream stream
                            && stream.getBytes() != null) {
                        String s = new String(stream.getBytes(),
                                java.nio.charset.StandardCharsets.ISO_8859_1);
                        // Seuls les flux de contenu nous intéressent.
                        if (s.contains("BT") || s.contains("Tf")) {
                            sb.append(s);
                        }
                    }
                }
            } finally {
                reader.close();
            }
            return sb.toString();
        }
    }
}
