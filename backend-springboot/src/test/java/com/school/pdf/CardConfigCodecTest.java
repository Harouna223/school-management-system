package com.school.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lecture, écriture et validation de la configuration de la carte scolaire.
 *
 * <p>Cette classe est le seul endroit où une valeur peut entrer dans le PDF de
 * la carte. Deux propriétés y sont verrouillées :</p>
 * <ol>
 *   <li><b>Le défaut ne change rien.</b> {@link CardConfig#defaults()} doit
 *       reproduire le rendu historique — sinon, dès le déploiement, toutes les
 *       cartes déjà imprimées changeraient d'apparence sans que personne n'ait
 *       rien demandé.</li>
 *   <li><b>L'aller-retour est stable.</b> Ce qui est enregistré est réencodé
 *       depuis l'objet décodé ; si {@code encode(decode(x)) != x}, l'écran
 *       afficherait après enregistrement autre chose que ce que
 *       l'administrateur vient de régler.</li>
 * </ol>
 */
class CardConfigCodecTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String raw) {
        try {
            return MAPPER.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ==================================================================
    // Valeurs d'origine
    // ==================================================================

    @Nested
    @DisplayName("Valeurs d'origine")
    class Defauts {

        @Test
        @DisplayName("reproduisent le rendu historique de la carte")
        void renduHistorique() {
            CardConfig c = CardConfig.defaults();
            // Bordure noire 0,9 pt à 3 pt des bords.
            assertThat(c.showBorder).isTrue();
            assertThat(c.borderColor).isEqualTo(java.awt.Color.BLACK);
            assertThat(c.borderWidth).isEqualTo(0.9f);
            // Le rendu historique place le LOGO de l'école dans la pastille,
            // pas un emblème distinct : c'est cette valeur qui doit rester
            // par défaut, sinon la carte changerait dès la mise à jour.
            assertThat(c.emblemSource).isEqualTo(CardConfig.EmblemSource.SCHOOL_LOGO);
            assertThat(c.emblemSize).isEqualTo(22f);
            assertThat(c.showFlag).isTrue();
            assertThat(c.showHeader).isTrue();
            assertThat(c.headerLine1).isEqualTo("RÉPUBLIQUE DU MALI");
            assertThat(c.headerLine2).isEqualTo("Un Peuple – Un But – Une Foi");
            assertThat(c.headerLine3).isEqualTo("Ministère de l'Éducation Nationale");
            assertThat(c.title).isEqualTo("CARTE D'IDENTITÉ SCOLAIRE");
            assertThat(c.titleSize).isEqualTo(8.5f);
            assertThat(c.showBand).isTrue();
            assertThat(DocumentThemePresets.toHex(c.bandGreen)).isEqualTo("#14b53a");
            assertThat(DocumentThemePresets.toHex(c.bandYellow)).isEqualTo("#fcd116");
            assertThat(DocumentThemePresets.toHex(c.bandRed)).isEqualTo("#ce1126");
            assertThat(c.showSchoolName).isTrue();
            assertThat(c.schoolNameSize).isEqualTo(6.5f);
            // Le logo en bas à droite n'existait pas : il reste masqué.
            assertThat(c.showSchoolLogo).isFalse();
            assertThat(c.showPhotoFrame).isTrue();
            assertThat(c.footerText).isEqualTo("Signature et cachet du Directeur");
            assertThat(DocumentThemePresets.toHex(c.headingColor)).isEqualTo("#0f2a4e");
            assertThat(DocumentThemePresets.toHex(c.textColor)).isEqualTo("#28303a");
        }

        @Test
        @DisplayName("les sept champs d'identité sont imprimés, dans l'ordre du modèle")
        void septChamps() {
            assertThat(CardConfig.defaults().fields).containsExactly(
                    CardConfig.Field.LAST_NAME,
                    CardConfig.Field.FIRST_NAME,
                    CardConfig.Field.MATRICULE,
                    CardConfig.Field.BIRTH_DATE,
                    CardConfig.Field.BIRTH_PLACE,
                    CardConfig.Field.GENDER,
                    CardConfig.Field.CLASS_NAME);
        }

        @Test
        @DisplayName("le catalogue tient dans la hauteur disponible")
        void catalogueTientDansLaHauteur() {
            // Sept lignes de 8,5 pt entre h-61 et h-112 : au-delà, la huitième
            // ligne mordrait sur le pied de carte. Si un champ est ajouté à
            // l'énumération sans relever MAX_FIELDS, ce test le signale.
            assertThat(CardConfig.Field.values().length)
                    .isGreaterThanOrEqualTo(CardConfig.MAX_FIELDS);
        }

        @Test
        @DisplayName("un JSON absent renvoie les valeurs d'origine")
        void jsonAbsent() {
            assertThat(CardConfigCodec.encode(CardConfigCodec.decode(null)))
                    .isEqualTo(CardConfigCodec.encode(CardConfig.defaults()));
            assertThat(CardConfigCodec.encode(CardConfigCodec.decode(json("{}"))))
                    .isEqualTo(CardConfigCodec.encode(CardConfig.defaults()));
        }
    }

    // ==================================================================
    // Aller-retour
    // ==================================================================

    @Nested
    @DisplayName("Aller-retour")
    class AllerRetour {

        @Test
        @DisplayName("encode puis decode redonne exactement la même configuration")
        void stable() {
            JsonNode once = CardConfigCodec.encode(CardConfig.defaults());
            JsonNode twice = CardConfigCodec.encode(CardConfigCodec.decode(once));
            assertThat(twice).isEqualTo(once);
        }

        @Test
        @DisplayName("une configuration modifiée survit à l'aller-retour")
        void configurationModifiee() {
            CardConfig custom = CardConfig.defaults().toBuilder()
                    .showBorder(false)
                    .emblemSource(CardConfig.EmblemSource.EMBLEM)
                    .emblemSize(28f)
                    .title("CARTE SCOLAIRE")
                    .showBand(false)
                    .showSchoolLogo(true)
                    .fields(List.of(CardConfig.Field.LAST_NAME, CardConfig.Field.CLASS_NAME))
                    .build();

            JsonNode encoded = CardConfigCodec.encode(custom);
            CardConfig back = CardConfigCodec.decode(encoded);

            assertThat(back.showBorder).isFalse();
            assertThat(back.emblemSource).isEqualTo(CardConfig.EmblemSource.EMBLEM);
            assertThat(back.emblemSize).isEqualTo(28f);
            assertThat(back.title).isEqualTo("CARTE SCOLAIRE");
            assertThat(back.showBand).isFalse();
            assertThat(back.showSchoolLogo).isTrue();
            assertThat(back.fields).containsExactly(
                    CardConfig.Field.LAST_NAME, CardConfig.Field.CLASS_NAME);
            assertThat(CardConfigCodec.encode(back)).isEqualTo(encoded);
        }

        @Test
        @DisplayName("les clés inconnues ne sont jamais persistées")
        void clesInconnuesIgnorees() {
            JsonNode raw = json("{\"showBorder\": false, \"couleurInventee\": \"#123456\","
                    + " \"sous\": {\"bloc\": 1}}");
            JsonNode encoded = CardConfigCodec.encode(CardConfigCodec.decode(raw));
            assertThat(encoded.has("couleurInventee")).isFalse();
            assertThat(encoded.has("sous")).isFalse();
            assertThat(encoded.get("showBorder").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("les champs sont rétablis dans l'ordre du modèle, pas celui du client")
        void ordreCanonique() {
            // Un ordre libre permettrait de faire déborder la septième ligne :
            // l'interface choisit donc QUELS champs, jamais leur ordre.
            JsonNode raw = json("{\"fields\": [\"CLASS_NAME\", \"LAST_NAME\", \"GENDER\"]}");
            assertThat(CardConfigCodec.decode(raw).fields).containsExactly(
                    CardConfig.Field.LAST_NAME,
                    CardConfig.Field.GENDER,
                    CardConfig.Field.CLASS_NAME);
        }

        @Test
        @DisplayName("une taille hors bornes est ramenée dans les bornes à la lecture")
        void bornesALaLecture() {
            // À la lecture on ne refuse pas (un document doit toujours sortir),
            // mais on ne rend pas non plus une carte illisible.
            JsonNode raw = json("{\"emblemSize\": 200, \"titleSize\": 1, \"borderWidth\": 0}");
            CardConfig c = CardConfigCodec.decode(raw);
            assertThat(c.emblemSize).isEqualTo(30f);
            assertThat(c.titleSize).isEqualTo(6f);
            assertThat(c.borderWidth).isEqualTo(0.3f);
        }

        @Test
        @DisplayName("un en-tête de mauvaise longueur est ignoré, pas tronqué")
        void enteteMauvaiseLongueur() {
            JsonNode raw = json("{\"headerLines\": [\"A\", \"B\"]}");
            CardConfig c = CardConfigCodec.decode(raw);
            assertThat(c.headerLine1).isEqualTo("RÉPUBLIQUE DU MALI");
            assertThat(c.headerLine2).isEqualTo("Un Peuple – Un But – Une Foi");
            assertThat(c.headerLine3).isEqualTo("Ministère de l'Éducation Nationale");
        }

        @Test
        @DisplayName("une ligne d'en-tête vide reste vide (ligne masquée)")
        void ligneVideConservee() {
            JsonNode raw = json("{\"headerLines\": [\"RÉPUBLIQUE DU MALI\", \"\", \"\"]}");
            CardConfig c = CardConfigCodec.decode(raw);
            assertThat(c.headerLine1).isEqualTo("RÉPUBLIQUE DU MALI");
            assertThat(c.headerLine2).isEmpty();
            assertThat(c.headerLine3).isEmpty();
        }
    }

    // ==================================================================
    // Validation
    // ==================================================================

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("accepte les valeurs d'origine")
        void accepteDefauts() {
            CardConfigCodec.validate(CardConfigCodec.encode(CardConfig.defaults()));
        }

        @Test
        @DisplayName("accepte une couleur abrégée (#fff)")
        void couleurAbregee() {
            CardConfigCodec.validate(json("{\"borderColor\": \"#fff\"}"));
        }

        @Test
        @DisplayName("refuse une couleur mal formée")
        void couleurInvalide() {
            assertThatThrownBy(() -> CardConfigCodec.validate(json("{\"borderColor\": \"rouge\"}")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("borderColor")
                    .hasMessageContaining("#RRGGBB");
        }

        @Test
        @DisplayName("refuse une couleur de bande mal formée")
        void couleurBandeInvalide() {
            assertThatThrownBy(() -> CardConfigCodec.validate(
                    json("{\"bandColors\": {\"green\": \"#14b53a\", \"yellow\": \"#zzzzzz\"}}")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("yellow");
        }

        @Test
        @DisplayName("refuse une provenance d'emblème inconnue")
        void emblemeInconnu() {
            assertThatThrownBy(() -> CardConfigCodec.validate(json("{\"emblemSource\": \"ARMOIRIES\"}")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("ARMOIRIES");
        }

        @Test
        @DisplayName("refuse une taille hors bornes")
        void tailleHorsBornes() {
            assertThatThrownBy(() -> CardConfigCodec.validate(json("{\"emblemSize\": 40}")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("emblemSize");
        }

        @Test
        @DisplayName("refuse une valeur non numérique là où un nombre est attendu")
        void tailleNonNumerique() {
            assertThatThrownBy(() -> CardConfigCodec.validate(json("{\"titleSize\": \"huit\"}")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("titleSize");
        }

        @Test
        @DisplayName("refuse une carte sans aucun champ d'identité")
        void aucunChamp() {
            assertThatThrownBy(() -> CardConfigCodec.validate(json("{\"fields\": []}")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Au moins un champ");
        }

        @Test
        @DisplayName("refuse un champ d'identité inconnu")
        void champInconnu() {
            assertThatThrownBy(() -> CardConfigCodec.validate(json("{\"fields\": [\"TAILLE\"]}")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("TAILLE");
        }

        @Test
        @DisplayName("refuse un champ d'identité en double")
        void champEnDouble() {
            assertThatThrownBy(() -> CardConfigCodec.validate(
                    json("{\"fields\": [\"LAST_NAME\", \"LAST_NAME\"]}")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("double");
        }

        @Test
        @DisplayName("refuse plus de champs que la hauteur n'en accepte")
        void tropDeChamps() {
            assertThatThrownBy(() -> CardConfigCodec.validate(json(
                    "{\"fields\": [\"LAST_NAME\",\"FIRST_NAME\",\"MATRICULE\",\"BIRTH_DATE\","
                            + "\"BIRTH_PLACE\",\"GENDER\",\"CLASS_NAME\",\"INCONNU\"]}")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Au plus 7");
        }

        @Test
        @DisplayName("refuse un en-tête qui ne comporte pas trois lignes")
        void enteteIncomplet() {
            assertThatThrownBy(() -> CardConfigCodec.validate(json("{\"headerLines\": [\"A\"]}")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("trois lignes");
        }

        @Test
        @DisplayName("refuse un titre trop long pour tenir dans la carte")
        void titreTropLong() {
            String trop = "C".repeat(61);
            assertThatThrownBy(() -> CardConfigCodec.validate(
                    json("{\"title\": \"" + trop + "\"}")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("60");
        }
    }

    // ==================================================================
    // Description exposée au client
    // ==================================================================

    @Nested
    @DisplayName("Description exposée au client")
    class Description {

        @Test
        @DisplayName("expose la configuration et le catalogue des champs")
        @SuppressWarnings("unchecked")
        void catalogue() {
            Map<String, Object> d = CardConfigCodec.describe(CardConfig.defaults());
            assertThat(d).containsKeys("config", "emblemSources", "availableFields", "maxFields");
            assertThat((List<Map<String, String>>) d.get("availableFields"))
                    .extracting(m -> m.get("code"))
                    .containsExactly("LAST_NAME", "FIRST_NAME", "MATRICULE", "BIRTH_DATE",
                            "BIRTH_PLACE", "GENDER", "CLASS_NAME");
            assertThat((List<Map<String, String>>) d.get("emblemSources"))
                    .extracting(m -> m.get("code"))
                    .containsExactly("NONE", "EMBLEM", "SCHOOL_LOGO");
            assertThat(d.get("maxFields")).isEqualTo(CardConfig.MAX_FIELDS);
        }

        @Test
        @DisplayName("le JSON décrit contient toutes les clés attendues")
        void clesCompletes() {
            JsonNode cfg = (JsonNode) CardConfigCodec.describe(CardConfig.defaults()).get("config");
            java.util.List<String> names = new java.util.ArrayList<>();
            cfg.fieldNames().forEachRemaining(names::add);
            assertThat(names).containsExactlyInAnyOrder(
                    "showBorder", "borderColor", "borderWidth", "emblemSource", "emblemSize",
                    "showFlag", "showHeader", "headerLines", "title", "titleSize",
                    "showBand", "bandColors", "showSchoolName", "schoolNameSize",
                    "showSchoolLogo", "schoolLogoSize", "showPhotoFrame", "photoFrameColor",
                    "fields", "footerText", "headingColor", "textColor");
        }
    }
}
