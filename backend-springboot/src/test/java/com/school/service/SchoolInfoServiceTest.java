package com.school.service;

import com.school.config.AppProperties;
import com.school.dto.request.SchoolInfoRequest;
import com.school.entity.Setting;
import com.school.exception.BusinessException;
import com.school.repository.SettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests du module « Informations de l'établissement ».
 *
 * <p>Vérifie la lecture (avec replis), le refus des numéros invalides et des
 * années futures. Les champs facultatifs vides ne doivent jamais bloquer.</p>
 */
@ExtendWith(MockitoExtension.class)
class SchoolInfoServiceTest {

    @Mock private SettingRepository settingRepository;
    @Mock private AuditService auditService;

    private SettingService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getSchool().setName("École config");
        properties.getSchool().setAddress("Adresse config");
        properties.getSchool().setPhone("+223 65 23 94 88");
        properties.getSchool().setEmail("contact@ecole.ml");
        service = new SettingService(settingRepository, properties, auditService);
    }

    private static Setting setting(String key, String value) {
        return Setting.builder().key(key).value(value).build();
    }

    @Test
    @DisplayName("Les informations sont lues depuis la base, avec repli sur la configuration")
    void litLesInformationsAvecRepli() {
        // Le stub générique est posé EN PREMIER : les stubs spécifiques qui
        // suivent ont la priorité (Mockito retient le dernier qui matche).
        when(settingRepository.findById(any(String.class))).thenReturn(Optional.empty());
        when(settingRepository.findById("SCHOOL_NAME"))
                .thenReturn(Optional.of(setting("SCHOOL_NAME", "École Sahel")));
        when(settingRepository.findById("SCHOOL_COUNTRY"))
                .thenReturn(Optional.of(setting("SCHOOL_COUNTRY", "Mali")));

        var info = service.schoolInfo();

        assertThat(info.getName()).isEqualTo("École Sahel");
        assertThat(info.getAddress()).isEqualTo("Adresse config");
        assertThat(info.getCountry()).isEqualTo("Mali");
    }

    @Test
    @DisplayName("Un numéro de téléphone trop court est refusé")
    void telephoneInvalide() {
        SchoolInfoRequest request = new SchoolInfoRequest();
        request.setName("École");
        request.setPhone("12");

        assertThatThrownBy(() -> service.updateSchoolInfo(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("téléphone");
    }

    @Test
    @DisplayName("Un numéro malien avec espaces et préfixe international est accepté")
    void telephoneMalienAccepte() {
        when(settingRepository.findById(any(String.class))).thenReturn(Optional.empty());
        when(settingRepository.save(any(Setting.class))).thenAnswer(inv -> inv.getArgument(0));

        SchoolInfoRequest request = new SchoolInfoRequest();
        request.setName("École");
        request.setPhone("+223 65-23-94-88");

        service.updateSchoolInfo(request, null);

        org.mockito.ArgumentCaptor<Setting> captor = org.mockito.ArgumentCaptor.forClass(Setting.class);
        org.mockito.Mockito.verify(settingRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(s -> SettingService.SCHOOL_NAME.equals(s.getKey()) && "École".equals(s.getValue()))
                .anyMatch(s -> SettingService.SCHOOL_PHONE.equals(s.getKey())
                        && "+223 65-23-94-88".equals(s.getValue()));
    }

    @Test
    @DisplayName("Une année de création dans le futur est refusée")
    void anneeFutureRefusee() {
        SchoolInfoRequest request = new SchoolInfoRequest();
        request.setName("École");
        request.setCreationYear(3000);

        assertThatThrownBy(() -> service.updateSchoolInfo(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("futur");
    }

    @Test
    @DisplayName("Les champs facultatifs vides n'empêchent pas l'enregistrement")
    void champsFacultatifsVides() {
        when(settingRepository.findById(any(String.class))).thenReturn(Optional.empty());
        when(settingRepository.save(any(Setting.class))).thenAnswer(inv -> inv.getArgument(0));

        SchoolInfoRequest request = new SchoolInfoRequest();
        request.setName("École");
        // tous les autres champs restent null

        service.updateSchoolInfo(request, null);

        org.mockito.ArgumentCaptor<Setting> captor = org.mockito.ArgumentCaptor.forClass(Setting.class);
        org.mockito.Mockito.verify(settingRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(s -> SettingService.SCHOOL_NAME.equals(s.getKey()) && "École".equals(s.getValue()))
                .anyMatch(s -> SettingService.SCHOOL_COUNTRY.equals(s.getKey()) && "".equals(s.getValue()));
    }

    // ==================================================================
    // Indicatif téléphonique du pays
    // ==================================================================

    @Test
    @DisplayName("L'indicatif du pays est écrit sous la clé déjà lue par tous les envois")
    void indicatifEnregistreSousLaCleDesEnvois() {
        when(settingRepository.findById(any(String.class))).thenReturn(Optional.empty());
        when(settingRepository.save(any(Setting.class))).thenAnswer(inv -> inv.getArgument(0));

        SchoolInfoRequest request = new SchoolInfoRequest();
        request.setName("École");
        request.setPhoneCountryCode("+237");

        service.updateSchoolInfo(request, null);

        org.mockito.ArgumentCaptor<Setting> captor = org.mockito.ArgumentCaptor.forClass(Setting.class);
        org.mockito.Mockito.verify(settingRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .as("une clé distincte créerait deux valeurs concurrentes pour un même réglage")
                .anyMatch(s -> SettingService.WHATSAPP_DEFAULT_NUMBER.equals(s.getKey())
                        && "237".equals(s.getValue()));
    }

    @Test
    @DisplayName("L'indicatif est relu tel quel pour le formulaire")
    void indicatifReluDepuisLaBase() {
        when(settingRepository.findById(any(String.class))).thenReturn(Optional.empty());
        when(settingRepository.findById("WHATSAPP_DEFAULT_NUMBER"))
                .thenReturn(Optional.of(setting("WHATSAPP_DEFAULT_NUMBER", "223")));

        var info = service.schoolInfo();

        assertThat(info.getPhoneCountryCode()).isEqualTo("223");
    }

    @Test
    @DisplayName("Un indicatif de plus de 4 chiffres est refusé")
    void indicatifTropLongRefuse() {
        SchoolInfoRequest request = new SchoolInfoRequest();
        request.setName("École");
        request.setPhoneCountryCode("12345");

        assertThatThrownBy(() -> service.updateSchoolInfo(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("indicatif");
    }
}
