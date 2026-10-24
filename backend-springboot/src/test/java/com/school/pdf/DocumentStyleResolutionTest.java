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
}
