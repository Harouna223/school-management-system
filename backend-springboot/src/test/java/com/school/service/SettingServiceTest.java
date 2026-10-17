package com.school.service;

import com.school.config.AppProperties;
import com.school.dto.response.SettingResponse;
import com.school.entity.Setting;
import com.school.exception.BusinessException;
import com.school.repository.SettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Paramètres de l'établissement : identité, pied de reçu, WhatsApp, cycles actifs.
 *
 * <p>Ce service alimente indirectement <b>tous les documents PDF</b> (nom de l'école, adresse,
 * devise) et le drapeau WhatsApp. Deux invariants silencieux y sont verrouillés :</p>
 *
 * <ol>
 *   <li><b>{@code value()} traite une valeur vide comme une absence</b> ({@code filter(v -> !v.isBlank())}).
 *       Un administrateur qui <i>efface</i> le nom de l'école dans l'interface doit retomber sur la
 *       valeur de configuration, pas obtenir un en-tête de PDF vide. Retirer ce filtre ne casse rien
 *       visiblement : la base renvoie simplement la chaîne vide.</li>
 *   <li><b>{@code booleanValue()} ne retombe JAMAIS sur le défaut quand la clé existe.</b>
 *       {@code Boolean.parseBoolean} renvoie {@code false} pour toute chaîne autre que « true »
 *       (insensible à la casse) : « 1 », « oui », « OUI » ou même « » donnent {@code false}, et le
 *       défaut passé en argument est ignoré. C'est une <b>asymétrie assumée</b> avec {@code value()} :
 *       une valeur vide y vaut absence, ici elle vaut {@code false}.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class SettingServiceTest {

    private static final List<String> CYCLES_PAR_DEFAUT =
            List.of("JARDIN", "PRIMAIRE", "COLLEGE", "LYCEE", "UNIVERSITE");

    @Mock
    private SettingRepository settingRepository;
    @Mock
    private AppProperties appProperties;
    @Mock
    private AuditService auditService;

    private SettingService service;

    /** Dépôt en mémoire : les allers-retours écriture/relecture sont réellement testés. */
    private final Map<String, Setting> store = new HashMap<>();

    private final AppProperties.School school = new AppProperties.School();
    private final AppProperties.WhatsApp whatsapp = new AppProperties.WhatsApp();

    @BeforeEach
    void setUp() {
        service = new SettingService(settingRepository, appProperties, auditService);
        lenient().when(settingRepository.findById(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0, String.class))));
        lenient().when(settingRepository.save(any(Setting.class)))
                .thenAnswer(inv -> {
                    Setting s = inv.getArgument(0);
                    store.put(s.getKey(), s);
                    return s;
                });
        lenient().when(settingRepository.findAll()).thenAnswer(inv -> new ArrayList<>(store.values()));
    }

    private void stocke(String key, String value) {
        store.put(key, Setting.builder().key(key).value(value).build());
    }

    // ------------------------------------------------------------------
    // value() : la valeur vide vaut absence
    // ------------------------------------------------------------------

    @Test
    void value_renvoieLaValeurStockeeQuandElleEstRenseignee() {
        stocke(SettingService.SCHOOL_NAME, "Sahel Edu");

        assertThat(service.value(SettingService.SCHOOL_NAME, "defaut")).isEqualTo("Sahel Edu");
    }

    @Test
    void value_retombeSurLeDefautQuandLaCleEstAbsente() {
        assertThat(service.value(SettingService.SCHOOL_NAME, "defaut")).isEqualTo("defaut");
    }

    @Test
    void value_retombeSurLeDefautQuandLaValeurEstVide() {
        stocke(SettingService.SCHOOL_NAME, "");

        assertThat(service.value(SettingService.SCHOOL_NAME, "defaut"))
                .as("un nom d'école effacé doit retomber sur la configuration, pas vider l'en-tête des PDF")
                .isEqualTo("defaut");
    }

    @Test
    void value_retombeSurLeDefautQuandLaValeurNeContientQueDesEspaces() {
        stocke(SettingService.SCHOOL_NAME, "   ");

        assertThat(service.value(SettingService.SCHOOL_NAME, "defaut")).isEqualTo("defaut");
    }

    // ------------------------------------------------------------------
    // booleanValue() : parseBoolean ne rend jamais le defaut
    // ------------------------------------------------------------------

    @Test
    void booleanValue_retombeSurLeDefautQuandLaCleEstAbsente() {
        assertThat(service.booleanValue(SettingService.WHATSAPP_ENABLED, true)).isTrue();
        assertThat(service.booleanValue(SettingService.WHATSAPP_ENABLED, false)).isFalse();
    }

    @Test
    void booleanValue_litTrueEtFalseSansTenirCompteDeLaCasse() {
        stocke(SettingService.WHATSAPP_ENABLED, "true");
        assertThat(service.booleanValue(SettingService.WHATSAPP_ENABLED, false)).isTrue();

        stocke(SettingService.WHATSAPP_ENABLED, "TRUE");
        assertThat(service.booleanValue(SettingService.WHATSAPP_ENABLED, false)).isTrue();

        stocke(SettingService.WHATSAPP_ENABLED, "False");
        assertThat(service.booleanValue(SettingService.WHATSAPP_ENABLED, true)).isFalse();
    }

    @Test
    void booleanValue_renvoieFalsePourTouteAutreChaine_sansUtiliserLeDefaut() {
        // Piege : « 1 », « oui », « OUI » ne sont PAS vrais pour Boolean.parseBoolean.
        for (String saisie : List.of("1", "oui", "OUI", "yes", "actif")) {
            stocke(SettingService.WHATSAPP_ENABLED, saisie);
            assertThat(service.booleanValue(SettingService.WHATSAPP_ENABLED, true))
                    .as("« %s » doit valoir false, et surtout PAS retomber sur le defaut true", saisie)
                    .isFalse();
        }
    }

    @Test
    void booleanValue_neRetombePasSurLeDefautPourUneValeurVide_contrairementAValue() {
        stocke(SettingService.WHATSAPP_ENABLED, "");

        // Asymetrie assumee : value() traiterait "" comme une absence, booleanValue() comme false.
        assertThat(service.booleanValue(SettingService.WHATSAPP_ENABLED, true))
                .as("une valeur vide vaut false, pas le defaut")
                .isFalse();
        assertThat(service.value(SettingService.WHATSAPP_ENABLED, "defaut"))
                .as("le meme enregistrement, lu par value(), retombe bien sur le defaut")
                .isEqualTo("defaut");
    }

    // ------------------------------------------------------------------
    // updateAll() : validation des cles, valeur nulle, creation
    // ------------------------------------------------------------------

    @Test
    void updateAll_refuseUneCleMalFormee() {
        for (String cle : List.of("school_name", "SCHOOL-NAME", "SCHOOL NAME", "", "   ")) {
            assertThatThrownBy(() -> service.updateAll(Map.of(cle, "x"), null))
                    .as("la cle « %s » doit etre refusee", cle)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Clé de paramètre invalide");
        }
        verify(settingRepository, never()).save(any());
    }

    @Test
    void updateAll_refuseUneCleNulle() {
        Map<String, String> valeurs = new HashMap<>();
        valeurs.put(null, "x");

        assertThatThrownBy(() -> service.updateAll(valeurs, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Clé de paramètre invalide");
        verify(settingRepository, never()).save(any());
    }

    @Test
    void updateAll_convertitUneValeurNulleEnChaineVide() {
        Map<String, String> valeurs = new HashMap<>();
        valeurs.put(SettingService.SCHOOL_PHONE, null);

        service.updateAll(valeurs, null);

        assertThat(store.get(SettingService.SCHOOL_PHONE).getValue()).isEmpty();
    }

    @Test
    void updateAll_creeLeParametreAbsentEnConservantSaCle() {
        service.updateAll(Map.of("NOUVELLE_CLE", "valeur"), null);

        Setting cree = store.get("NOUVELLE_CLE");
        assertThat(cree).as("un parametre absent doit etre cree, cle comprise").isNotNull();
        assertThat(cree.getKey()).isEqualTo("NOUVELLE_CLE");
        assertThat(cree.getValue()).isEqualTo("valeur");
    }

    @Test
    void updateAll_journaliseLeNombreDeParametresEtRendLaListeTriee() {
        service.updateAll(Map.of("B_CLE", "1", "A_CLE", "2"), null);

        verify(auditService).log(org.mockito.ArgumentMatchers.eq("SETTINGS_UPDATE"),
                org.mockito.ArgumentMatchers.eq("Setting"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.contains("2 paramètre(s)"),
                org.mockito.ArgumentMatchers.isNull());

        assertThat(service.all()).extracting(SettingResponse::getKey)
                .containsExactly("A_CLE", "B_CLE");
    }

    // ------------------------------------------------------------------
    // Cycles actifs : aller-retour JSON
    // ------------------------------------------------------------------

    @Test
    void updateActiveCycles_refuseUneListeVide() {
        assertThatThrownBy(() -> service.updateActiveCycles(List.of(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Au moins un cycle doit être actif");
        assertThatThrownBy(() -> service.updateActiveCycles(null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Au moins un cycle doit être actif");
    }

    @Test
    void updateActiveCycles_faitUnAllerRetourJsonIdentique() {
        List<String> cycles = List.of("COLLEGE", "LYCEE");

        service.updateActiveCycles(cycles, null);

        assertThat(store.get(SettingService.ACTIVE_CYCLES).getValue())
                .as("le format stocke doit rester un tableau JSON de chaines")
                .isEqualTo("[\"COLLEGE\",\"LYCEE\"]");
        assertThat(service.getActiveCycles())
                .as("ce qui est ecrit doit etre relu a l'identique")
                .containsExactly("COLLEGE", "LYCEE");
    }

    @Test
    void getActiveCycles_retombeSurLesCinqCyclesParDefautSiLeJsonEstCorrompu() {
        stocke(SettingService.ACTIVE_CYCLES, "ceci n'est pas du json");

        // Recuperation silencieuse : une valeur corrompue ne doit pas faire tomber l'ecran.
        assertThat(service.getActiveCycles()).containsExactlyElementsOf(CYCLES_PAR_DEFAUT);
    }

    // ------------------------------------------------------------------
    // defaults() : valeurs de configuration, tolerantes au null
    // ------------------------------------------------------------------

    @Test
    void defaults_neLevePasSurUneConfigurationAbsente() {
        when(appProperties.getSchool()).thenReturn(school);
        when(appProperties.getWhatsapp()).thenReturn(whatsapp);
        // school.name reste null : aucune configuration app.school.name

        Map<String, String> defauts = service.defaults();

        assertThat(defauts.get(SettingService.SCHOOL_NAME)).isEmpty();
        assertThat(defauts.get(SettingService.SCHOOL_ADDRESS)).isEmpty();
        assertThat(defauts.get(SettingService.SCHOOL_SLOGAN)).isNotEmpty();
    }

    @Test
    void defaults_reprendLaConfigurationEtLeDrapeauWhatsapp() {
        school.setName("Sahel Edu");
        school.setPhone("+237600000000");
        whatsapp.setEnabled(true);
        when(appProperties.getSchool()).thenReturn(school);
        when(appProperties.getWhatsapp()).thenReturn(whatsapp);

        Map<String, String> defauts = service.defaults();

        assertThat(defauts.get(SettingService.SCHOOL_NAME)).isEqualTo("Sahel Edu");
        assertThat(defauts.get(SettingService.SCHOOL_PHONE)).isEqualTo("+237600000000");
        assertThat(defauts.get(SettingService.WHATSAPP_ENABLED)).isEqualTo("true");
        assertThat(defauts).hasSize(11);
    }
}
