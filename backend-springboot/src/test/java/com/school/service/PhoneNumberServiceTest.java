package com.school.service;

import com.school.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Normalisation des numéros de téléphone en E.164.
 *
 * <p>Ce service est appelé par {@code SmsService}, {@code WhatsAppService} et
 * {@code AttendanceService} pour joindre les <b>parents</b>. Un {@code null} ne lève rien : il
 * <b>annule silencieusement l'envoi</b>. Un indicatif résolu de travers n'échoue pas non plus, il
 * envoie au mauvais numéro. D'où les deux familles de tests ci-dessous : les bornes de validité, et
 * la chaîne de repli de l'indicatif.</p>
 *
 * <p>Invariant le plus subtil : {@code resolveCountryCode()} <b>écarte un paramètre qui ne contient
 * aucun chiffre</b> après nettoyage. Sans ce contrôle, un paramètre saisi « abc » donnerait un
 * indicatif vide, et {@code "76123456"} deviendrait {@code "+76123456"} — un E.164 parfaitement
 * valide… et parfaitement faux.</p>
 */
@ExtendWith(MockitoExtension.class)
class PhoneNumberServiceTest {

    private static final String CLE_INDICATIF = SettingService.WHATSAPP_DEFAULT_NUMBER;

    @Mock
    private SettingService settingService;
    @Mock
    private AppProperties appProperties;

    private PhoneNumberService service;

    private final AppProperties.WhatsApp whatsapp = new AppProperties.WhatsApp();

    @BeforeEach
    void setUp() {
        service = new PhoneNumberService(settingService, appProperties);
    }

    /** Indicateur pays lu depuis les paramètres de l'établissement. */
    private void indicatifEnBase(String valeur) {
        when(settingService.value(CLE_INDICATIF, "")).thenReturn(valeur);
    }

    /** Indicateur pays lu depuis la configuration applicative. */
    private void indicatifEnConfig(String valeur) {
        whatsapp.setDefaultCountryCode(valeur);
        when(appProperties.getWhatsapp()).thenReturn(whatsapp);
    }

    // ------------------------------------------------------------------
    // Entrées absentes ou sans chiffre : sortie immediate
    // ------------------------------------------------------------------

    @Test
    void normalize_renvoieNullPourUnNumeroAbsent() {
        assertThat(service.normalize(null)).isNull();
        assertThat(service.normalize("")).isNull();
        assertThat(service.normalize("   ")).isNull();
    }

    @Test
    void normalize_renvoieNullSansAucunChiffre_sansMemelesInterrogerLesParametres() {
        assertThat(service.normalize("abc")).isNull();
        assertThat(service.normalize("+")).isNull();
        assertThat(service.normalize("---")).isNull();

        // Sortie avant resolution de l'indicatif : aucun acces base.
        verifyNoInteractions(settingService);
    }

    // ------------------------------------------------------------------
    // Mise en forme
    // ------------------------------------------------------------------

    @Test
    void normalize_retireEspacesEtSeparateurs() {
        indicatifEnBase("223");

        assertThat(service.normalize("76 12 34 56")).isEqualTo("+22376123456");
        assertThat(service.normalize("76.12.34.56")).isEqualTo("+22376123456");
        assertThat(service.normalize("(76) 12-34-56")).isEqualTo("+22376123456");
    }

    @Test
    void normalize_remplaceLeZeroNationalParLIndicatif() {
        indicatifEnBase("223");

        assertThat(service.normalize("07 61 23 45 67")).isEqualTo("+223761234567");
    }

    @Test
    void normalize_neDoublePasUnIndicatifDejaPresent() {
        indicatifEnBase("223");

        assertThat(service.normalize("223 76 12 34 56"))
                .as("un numero deja prefixe par l'indicatif ne doit pas etre prefixe une 2e fois")
                .isEqualTo("+22376123456");
    }

    @Test
    void normalize_prefixeLIndicatifQuandIlManque() {
        indicatifEnBase("223");

        assertThat(service.normalize("76123456")).isEqualTo("+22376123456");
    }

    @Test
    void normalize_conserveUnNumeroDejaInternational() {
        indicatifEnBase("223");

        assertThat(service.normalize("+223 76 12 34 56")).isEqualTo("+22376123456");
    }

    @Test
    void normalize_faitConfianceAuPrefixePlusTelQuel() {
        indicatifEnBase("223");

        // Contrat : des qu'un "+" est present, le numero est considere deja international.
        assertThat(service.normalize("+076123456"))
                .as("le \"+\" est conserve verbatim, sans verification de l'indicatif")
                .isEqualTo("+076123456");
    }

    // ------------------------------------------------------------------
    // Bornes E.164 : 8 a 15 chiffres, sinon rejet silencieux
    // ------------------------------------------------------------------

    @Test
    void normalize_accepteLaBorneBasseDeHuitChiffres() {
        indicatifEnBase("223");

        // "223" + "76123" = 8 chiffres
        assertThat(service.normalize("76123")).isEqualTo("+22376123");
    }

    @Test
    void normalize_refuseUnNumeroTropCourt() {
        indicatifEnBase("223");

        // "223" + "7612" = 7 chiffres
        assertThat(service.normalize("7612"))
                .as("un numero trop court est rejete en silence : l'envoi sera simplement annule")
                .isNull();
    }

    @Test
    void normalize_accepteLaBorneHauteDeQuinzeChiffres() {
        indicatifEnBase("223");

        // "223" + 12 chiffres = 15
        assertThat(service.normalize("761234567890")).isEqualTo("+223761234567890");
    }

    @Test
    void normalize_refuseUnNumeroTropLong() {
        indicatifEnBase("223");

        // "223" + 13 chiffres = 16
        assertThat(service.normalize("7612345678901")).isNull();
    }

    // ------------------------------------------------------------------
    // Chaine de repli de l'indicatif : base -> configuration -> 237
    // ------------------------------------------------------------------

    @Test
    void resolveCountryCode_prioriteAuParametreEnBase() {
        indicatifEnBase("223");
        // La configuration n'est pas renseignee : si le service la consultait, il ne
        // trouverait rien et retomberait sur 237. Le resultat prouve deja la priorite...
        assertThat(service.normalize("76123456")).isEqualTo("+22376123456");
        // ...et ceci prouve mieux : la configuration n'est meme pas lue (court-circuit).
        verifyNoInteractions(appProperties);
    }

    @Test
    void resolveCountryCode_ecarteUnParametreSansChiffreEtRetombeSurLaConfiguration() {
        indicatifEnBase("abc");     // nettoye en "" -> doit etre ignore
        indicatifEnConfig("999");

        assertThat(service.normalize("76123456"))
                .as("un parametre vide apres nettoyage ne doit pas produire l'indicatif \"\" "
                        + "(sinon +76123456, un E.164 valide mais faux)")
                .isEqualTo("+99976123456");
    }

    @Test
    void resolveCountryCode_retombeSurLe237QuandLaConfigurationEstVide() {
        indicatifEnBase("");
        indicatifEnConfig(null);

        assertThat(service.normalize("76123456")).isEqualTo("+23776123456");
    }

    @Test
    void resolveCountryCode_neLevePasQuandLaConfigurationWhatsappEstAbsente() {
        indicatifEnBase("");
        when(appProperties.getWhatsapp()).thenReturn(null);

        assertThat(service.normalize("76123456")).isEqualTo("+23776123456");
    }

    @Test
    void resolveCountryCode_nettoieLesSeparateursDuParametre() {
        indicatifEnBase("+223");

        assertThat(service.normalize("76123456")).isEqualTo("+22376123456");
    }
}
