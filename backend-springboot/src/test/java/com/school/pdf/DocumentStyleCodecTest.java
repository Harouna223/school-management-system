package com.school.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Configuration de présentation des documents : décodage, validation,
 * préréglages et surcharges.
 *
 * <p>Ces tests ne dessinent rien : ils verrouillent les <b>décisions</b>
 * qui décident du rendu d'un PDF — quelles couleurs gagnent, quelles valeurs
 * sont refusées, et surtout qu'une configuration absente ou incomplète
 * retombe exactement sur la charte historique.</p>
 */
class DocumentStyleCodecTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonNode json(String raw) {
        try {
            return MAPPER.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Nested
    @DisplayName("Charte par défaut")
    class Defaults {

        @Test
        @DisplayName("sans configuration, les constantes historiques sont utilisées telles quelles")
        void sansConfigurationCharteHistorique() {
            DocumentStyle s = DocumentStyleCodec.decode(null);

            assertThat(s.primary).isEqualTo(SchoolDocumentTheme.PRIMARY);
            assertThat(s.text).isEqualTo(SchoolDocumentTheme.TEXT_DARK);
            assertThat(s.border).isEqualTo(SchoolDocumentTheme.BORDER);
            assertThat(s.fontFamily).isEqualTo("HELVETICA");
            assertThat(s.bodySize).isEqualTo(11f);
            assertThat(s.pageFormat).isEqualTo(DocumentStyle.PageFormat.A4);
            assertThat(s.orientation).isEqualTo(DocumentStyle.Orientation.PORTRAIT);
            assertThat(s.zebraStripes).isTrue();
        }

        @Test
        @DisplayName("un JSON vide ne casse pas le rendu")
        void jsonVideResteExploitable() {
            DocumentStyle s = DocumentStyleCodec.decode(json("{}"));

            assertThat(s.titleSize).isEqualTo(16f);
            assertThat(s.showLogo).isTrue();
            assertThat(s.logoPosition).isEqualTo(DocumentStyle.LogoPosition.LEFT);
        }

        @Test
        @DisplayName("un JSON corrompu ne lève pas : le document doit toujours pouvoir sortir")
        void jsonCorrompuNeLevePas() {
            assertThat(DocumentStyleCodec.parse("{ pas du json "))
                    .as("un contenu illisible est traité comme absent")
                    .isNull();
            assertThat(DocumentStyleCodec.decode(DocumentStyleCodec.parse("{ pas du json ")).primary)
                    .isEqualTo(SchoolDocumentTheme.PRIMARY);
        }
    }

    @Nested
    @DisplayName("Modèles graphiques")
    class Templates {

        @Test
        @DisplayName("chaque modèle est une variante de mise en page, pas un simple renommage")
        void chaqueModeleChangeLaMiseEnPage() {
            DocumentStyle base = DocumentStyle.defaults();
            DocumentStyle modern = DocumentThemePresets.applyTemplate("MODERN", base);
            DocumentStyle premium = DocumentThemePresets.applyTemplate("PREMIUM", base);
            DocumentStyle minimal = DocumentThemePresets.applyTemplate("MINIMAL", base);

            assertThat(modern.logoPosition).isEqualTo(DocumentStyle.LogoPosition.CENTER);
            assertThat(modern.titleAlign).isEqualTo(DocumentStyle.TitleAlign.CENTER);
            assertThat(premium.fontFamily).isEqualTo("TIMES");
            assertThat(premium.tableBorders).isTrue();
            assertThat(minimal.headerRule).isFalse();
            assertThat(minimal.bodySize).isLessThan(base.bodySize);
            assertThat(minimal.marginLeft).isLessThan(base.marginLeft);
        }

        @Test
        @DisplayName("changer de modèle ne touche pas aux couleurs de l'établissement")
        void modeleNeVolatilisePasLaPalette() {
            DocumentStyle base = DocumentStyle.defaults().toBuilder()
                    .primary(new Color(20, 83, 45))
                    .build();

            DocumentStyle minimal = DocumentThemePresets.applyTemplate("MINIMAL", base);

            assertThat(minimal.primary).isEqualTo(new Color(20, 83, 45));
        }

        @Test
        @DisplayName("le thème d'origine n'est jamais modifié en place")
        void applicationImmutable() {
            DocumentStyle base = DocumentStyle.defaults();
            DocumentThemePresets.applyTemplate("PREMIUM", base);

            assertThat(base.fontFamily).isEqualTo("HELVETICA");
        }

        @Test
        @DisplayName("un modèle inconnu retombe en « personnalisé » sans casser le rendu")
        void modeleInconnu() {
            DocumentStyle s = DocumentThemePresets.applyTemplate("NEXISTEPAS", DocumentStyle.defaults());

            assertThat(s.template).isEqualTo(DocumentStyle.Template.CUSTOM);
            assertThat(s.bodySize).isEqualTo(11f);
        }
    }

    @Nested
    @DisplayName("Palettes et couleurs")
    class Colors {

        @Test
        @DisplayName("une palette redéfinit les neuf couleurs documentaires")
        void paletteComplete() {
            DocumentStyle s = DocumentThemePresets.applyPalette("ACADEMIC_GREEN", DocumentStyle.defaults());

            assertThat(DocumentThemePresets.toHex(s.primary)).isEqualTo("#14532d");
            assertThat(DocumentThemePresets.toHex(s.tableHeader)).isEqualTo("#166534");
            assertThat(DocumentThemePresets.toHex(s.sectionBackground)).isEqualTo("#ecfdf5");
        }

        @Test
        @DisplayName("les couleurs individuelles gagnent sur la palette nommée")
        void couleurIndividuelleGagneSurLaPalette() {
            DocumentStyle s = DocumentStyleCodec.decode(json("""
                    {"template":"CLASSIC","palette":"ACADEMIC_GREEN",
                     "colors":{"primary":"#ff0000"}}
                    """));

            assertThat(DocumentThemePresets.toHex(s.primary)).isEqualTo("#ff0000");
            assertThat(DocumentThemePresets.toHex(s.tableHeader))
                    .as("les autres couleurs de la palette restent appliquées")
                    .isEqualTo("#166534");
        }

        @Test
        @DisplayName("les formats #rgb et #RRGGBB sont acceptés, le reste est rejeté")
        void analyseHex() {
            assertThat(DocumentThemePresets.hex("#fff")).isEqualTo(Color.WHITE);
            assertThat(DocumentThemePresets.hex("1e3a8a")).isEqualTo(new Color(30, 58, 138));
            assertThat(DocumentThemePresets.hex("#GGGGGG")).isNull();
            assertThat(DocumentThemePresets.hex("rouge")).isNull();
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("une couleur mal formée est refusée à l'enregistrement, pas remplacée en silence")
        void couleurInvalide() {
            JsonNode config = json("{\"colors\":{\"primary\":\"bleu\"}}");

            assertThatThrownBy(() -> DocumentStyleCodec.validate(config))
                    .hasMessageContaining("primary")
                    .hasMessageContaining("#RRGGBB");
        }

        @Test
        @DisplayName("une taille de police hors bornes est refusée")
        void tailleHorsBornes() {
            JsonNode config = json("{\"typography\":{\"bodySize\":40}}");

            assertThatThrownBy(() -> DocumentStyleCodec.validate(config))
                    .hasMessageContaining("bodySize");
        }

        @Test
        @DisplayName("une marge négative ou démesurée est refusée")
        void margeHorsBornes() {
            assertThatThrownBy(() -> DocumentStyleCodec.validate(json("{\"layout\":{\"marginTop\":2}}")))
                    .hasMessageContaining("marginTop");
            assertThatThrownBy(() -> DocumentStyleCodec.validate(json("{\"layout\":{\"marginLeft\":400}}")))
                    .hasMessageContaining("marginLeft");
        }

        @Test
        @DisplayName("une police non livrée avec le moteur PDF est refusée")
        void policeInconnue() {
            assertThatThrownBy(() -> DocumentStyleCodec.validate(
                    json("{\"typography\":{\"fontFamily\":\"COMIC SANS\"}}")))
                    .as("aucune intégration de fichier de police : le rendu échouerait")
                    .hasMessageContaining("Police non prise en charge");
        }

        @Test
        @DisplayName("un format de page ou une orientation inconnus sont refusés")
        void pageEtOrientation() {
            assertThatThrownBy(() -> DocumentStyleCodec.validate(json("{\"layout\":{\"pageFormat\":\"A3\"}}")))
                    .hasMessageContaining("Format de page");
            assertThatThrownBy(() -> DocumentStyleCodec.validate(json("{\"layout\":{\"orientation\":\"DIAGONALE\"}}")))
                    .hasMessageContaining("Orientation");
        }

        @Test
        @DisplayName("un texte libre trop long est refusé (injection d'en-tête HTTP impossible)")
        void texteTropLong() {
            String longText = "x".repeat(301);
            assertThatThrownBy(() -> DocumentStyleCodec.validate(
                    json("{\"footerText\":\"" + longText + "\"}")))
                    .hasMessageContaining("300");
        }

        @Test
        @DisplayName("une configuration valide passe")
        void configurationValide() {
            DocumentStyleCodec.validate(json("""
                    {"template":"MODERN","palette":"PROFESSIONAL_GREY",
                     "colors":{"primary":"#334155","headerText":"#ffffff"},
                     "typography":{"fontFamily":"TIMES","bodySize":10.5,"tableSize":8.5},
                     "layout":{"pageFormat":"A4","orientation":"LANDSCAPE","marginTop":36,
                               "marginRight":36,"marginBottom":70,"marginLeft":36},
                     "footerText":"Document interne","titleAlign":"CENTER"}
                    """));
        }
    }

    @Nested
    @DisplayName("Contraste")
    class Contrast {

        @Test
        @DisplayName("le ratio est calculé en luminance relative (WCAG), donc valable en noir et blanc")
        void contrasteNoirBlanc() {
            assertThat(DocumentStyleCodec.contrast(Color.BLACK, Color.WHITE))
                    .isCloseTo(21.0, org.assertj.core.data.Offset.offset(0.1));
            assertThat(DocumentStyleCodec.contrast(Color.WHITE, Color.WHITE))
                    .isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
        }

        @Test
        @DisplayName("un texte blanc sur fond jaune vif est signalé")
        void texteIllisibleSignale() {
            DocumentStyle s = DocumentStyle.defaults().toBuilder()
                    .tableHeader(new Color(255, 235, 59))
                    .headerText(Color.WHITE)
                    .build();

            assertThat(DocumentStyleCodec.contrastWarnings(s))
                    .anyMatch(w -> w.contains("en-têtes de tableaux"));
        }

        @Test
        @DisplayName("la charte par défaut ne produit aucun avertissement")
        void charteParDefautSansAvertissement() {
            assertThat(DocumentStyleCodec.contrastWarnings(DocumentStyle.defaults())).isEmpty();
        }
    }

    @Nested
    @DisplayName("Encodage / décodage")
    class RoundTrip {

        @Test
        @DisplayName("une configuration relue à l'identique produit la même apparence")
        void allerRetour() {
            DocumentStyle original = DocumentThemePresets.applyTemplate("PREMIUM",
                    DocumentThemePresets.applyPalette("ELEGANT_BURGUNDY", DocumentStyle.defaults()))
                    .toBuilder()
                    .bodySize(12f)
                    .footerText2("Texte du pied")
                    .build();

            DocumentStyle relu = DocumentStyleCodec.decode(
                    DocumentStyleCodec.parse(DocumentStyleCodec.stringify(
                            DocumentStyleCodec.encode(original))));

            assertThat(DocumentThemePresets.toHex(relu.primary))
                    .isEqualTo(DocumentThemePresets.toHex(original.primary));
            assertThat(relu.fontFamily).isEqualTo(original.fontFamily);
            assertThat(relu.bodySize).isEqualTo(original.bodySize);
            assertThat(relu.footerText2).isEqualTo("Texte du pied");
            assertThat(relu.tableBorders).isEqualTo(original.tableBorders);
        }
    }
}
