package com.school.service;

import com.school.config.AppProperties;
import com.school.dto.response.SettingResponse;
import com.school.entity.Setting;
import com.school.repository.SettingRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Paramètres de l'établissement stockés en base et éditables.
 */
@Service
@RequiredArgsConstructor
public class SettingService {

    public static final String SCHOOL_NAME = "SCHOOL_NAME";
    public static final String SCHOOL_ADDRESS = "SCHOOL_ADDRESS";
    public static final String SCHOOL_PHONE = "SCHOOL_PHONE";
    public static final String SCHOOL_EMAIL = "SCHOOL_EMAIL";
    public static final String SCHOOL_SLOGAN = "SCHOOL_SLOGAN";
    public static final String SCHOOL_WEBSITE = "SCHOOL_WEBSITE";
    public static final String SCHOOL_LOGO = "SCHOOL_LOGO";
    // Identité complémentaire (module « Informations de l'établissement »)
    public static final String SCHOOL_SHORT_NAME = "SCHOOL_SHORT_NAME";
    public static final String SCHOOL_TYPE = "SCHOOL_TYPE";
    public static final String SCHOOL_STATUS = "SCHOOL_STATUS";
    public static final String SCHOOL_ID_NUMBER = "SCHOOL_ID_NUMBER";
    public static final String SCHOOL_CREATION_YEAR = "SCHOOL_CREATION_YEAR";
    // Coordonnées détaillées
    public static final String SCHOOL_DISTRICT = "SCHOOL_DISTRICT";
    public static final String SCHOOL_CITY = "SCHOOL_CITY";
    public static final String SCHOOL_REGION = "SCHOOL_REGION";
    public static final String SCHOOL_COUNTRY = "SCHOOL_COUNTRY";
    public static final String SCHOOL_PHONE_ALT = "SCHOOL_PHONE_ALT";
    // Administration
    public static final String SCHOOL_DIRECTOR = "SCHOOL_DIRECTOR";
    public static final String SCHOOL_DIRECTOR_ROLE = "SCHOOL_DIRECTOR_ROLE";
    public static final String SCHOOL_DIRECTOR_PHONE = "SCHOOL_DIRECTOR_PHONE";
    public static final String SCHOOL_DIRECTOR_EMAIL = "SCHOOL_DIRECTOR_EMAIL";
    public static final String SCHOOL_ACCREDITATION = "SCHOOL_ACCREDITATION";
    public static final String SCHOOL_ADDITIONAL_INFO = "SCHOOL_ADDITIONAL_INFO";
    public static final String RECEIPT_FOOTER = "RECEIPT_FOOTER";
    public static final String WHATSAPP_ENABLED = "WHATSAPP_ENABLED";
    public static final String WHATSAPP_DEFAULT_NUMBER = "WHATSAPP_DEFAULT_NUMBER";
    public static final String ACTIVE_CYCLES = "ACTIVE_CYCLES";

    private final SettingRepository settingRepository;
    private final AppProperties appProperties;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<SettingResponse> all() {
        return settingRepository.findAll().stream()
                .sorted(Comparator.comparing(Setting::getKey))
                .map(SettingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public String value(String key, String fallback) {
        return settingRepository.findById(key)
                .map(Setting::getValue)
                .filter(v -> !v.isBlank())
                .orElse(fallback);
    }

    @Transactional(readOnly = true)
    public boolean booleanValue(String key, boolean fallback) {
        return settingRepository.findById(key)
                .map(Setting::getValue)
                .map(Boolean::parseBoolean)
                .orElse(fallback);
    }

    /**
     * Met à jour les paramètres fournis (création si absent), journalisé en audit.
     */
    @Transactional
    public List<SettingResponse> updateAll(Map<String, String> values, HttpServletRequest httpRequest) {
        values.forEach((key, raw) -> {
            if (key == null || key.isBlank() || !key.matches("[A-Z0-9_]+")) {
                throw new com.school.exception.BusinessException("Clé de paramètre invalide : " + key);
            }
            String value = raw == null ? "" : raw;
            Setting setting = settingRepository.findById(key).orElseGet(() -> Setting.builder().key(key).build());
            setting.setValue(value);
            settingRepository.save(setting);
        });
        auditService.log("SETTINGS_UPDATE", "Setting", null,
                values.size() + " paramètre(s) mis à jour", httpRequest);
        return all();
    }

    /**
     * Valeurs par défaut : priorité à la base, sinon au fichier de configuration.
     */
    public Map<String, String> defaults() {
        return Map.ofEntries(
                Map.entry(SCHOOL_NAME, Optional.ofNullable(appProperties.getSchool().getName()).orElse("")),
                Map.entry(SCHOOL_ADDRESS, Optional.ofNullable(appProperties.getSchool().getAddress()).orElse("")),
                Map.entry(SCHOOL_PHONE, Optional.ofNullable(appProperties.getSchool().getPhone()).orElse("")),
                Map.entry(SCHOOL_EMAIL, Optional.ofNullable(appProperties.getSchool().getEmail()).orElse("")),
                Map.entry(SCHOOL_SLOGAN, "Éducation, Excellence et Avenir"),
                Map.entry(SCHOOL_WEBSITE, ""),
                Map.entry(SCHOOL_LOGO, ""),
                Map.entry(SCHOOL_SHORT_NAME, ""),
                Map.entry(SCHOOL_TYPE, ""),
                Map.entry(SCHOOL_STATUS, ""),
                Map.entry(SCHOOL_ID_NUMBER, ""),
                Map.entry(SCHOOL_CREATION_YEAR, ""),
                Map.entry(SCHOOL_DISTRICT, ""),
                Map.entry(SCHOOL_CITY, ""),
                Map.entry(SCHOOL_REGION, ""),
                Map.entry(SCHOOL_COUNTRY, "Mali"),
                Map.entry(SCHOOL_PHONE_ALT, ""),
                Map.entry(SCHOOL_DIRECTOR, ""),
                Map.entry(SCHOOL_DIRECTOR_ROLE, "Directeur"),
                Map.entry(SCHOOL_DIRECTOR_PHONE, ""),
                Map.entry(SCHOOL_DIRECTOR_EMAIL, ""),
                Map.entry(SCHOOL_ACCREDITATION, ""),
                Map.entry(SCHOOL_ADDITIONAL_INFO, ""),
                Map.entry(RECEIPT_FOOTER, "Merci de votre confiance."),
                Map.entry(WHATSAPP_ENABLED, String.valueOf(appProperties.getWhatsapp().isEnabled())),
                Map.entry(WHATSAPP_DEFAULT_NUMBER, "237"),
                Map.entry(ACTIVE_CYCLES, "[\"JARDIN\",\"PRIMAIRE\",\"COLLEGE\",\"LYCEE\",\"UNIVERSITE\"]")
        );
    }

    @Transactional(readOnly = true)
    public List<String> getActiveCycles() {
        return parseCycles(value(ACTIVE_CYCLES, "[\"JARDIN\",\"PRIMAIRE\",\"COLLEGE\",\"LYCEE\",\"UNIVERSITE\"]"));
    }

    @Transactional
    public List<String> updateActiveCycles(List<String> cycles, HttpServletRequest httpRequest) {
        if (cycles == null || cycles.isEmpty()) {
            throw new com.school.exception.BusinessException("Au moins un cycle doit être actif");
        }
        String json = "[" + cycles.stream().map(c -> "\"" + c + "\"").collect(java.util.stream.Collectors.joining(",")) + "]";
        updateAll(Map.of(ACTIVE_CYCLES, json), httpRequest);
        return cycles;
    }

    // ==================================================================
    // Informations de l'établissement (module dédié)
    // ==================================================================

    /**
     * Informations officielles, lues depuis la table {@code settings}.
     *
     * <p>Aucune requête par champ : {@link #defaults()} fournit les replis, et
     * seules les clés réellement enregistrées sont lues.</p>
     */
    @Transactional(readOnly = true)
    public com.school.dto.response.SchoolInfoResponse schoolInfo() {
        Map<String, String> d = defaults();
        return com.school.dto.response.SchoolInfoResponse.builder()
                .name(stored(SCHOOL_NAME, d.get(SCHOOL_NAME)))
                .shortName(stored(SCHOOL_SHORT_NAME, ""))
                .type(stored(SCHOOL_TYPE, ""))
                .status(stored(SCHOOL_STATUS, ""))
                .identificationNumber(stored(SCHOOL_ID_NUMBER, ""))
                .motto(stored(SCHOOL_SLOGAN, d.get(SCHOOL_SLOGAN)))
                .creationYear(parseYear(stored(SCHOOL_CREATION_YEAR, "")))
                .address(stored(SCHOOL_ADDRESS, d.get(SCHOOL_ADDRESS)))
                .district(stored(SCHOOL_DISTRICT, ""))
                .city(stored(SCHOOL_CITY, ""))
                .region(stored(SCHOOL_REGION, ""))
                .country(stored(SCHOOL_COUNTRY, "Mali"))
                .phoneCountryCode(stored(WHATSAPP_DEFAULT_NUMBER, d.get(WHATSAPP_DEFAULT_NUMBER)))
                .phone(stored(SCHOOL_PHONE, d.get(SCHOOL_PHONE)))
                .phoneAlt(stored(SCHOOL_PHONE_ALT, ""))
                .email(stored(SCHOOL_EMAIL, d.get(SCHOOL_EMAIL)))
                .website(stored(SCHOOL_WEBSITE, ""))
                .directorName(stored(SCHOOL_DIRECTOR, ""))
                .directorRole(stored(SCHOOL_DIRECTOR_ROLE, "Directeur"))
                .directorPhone(stored(SCHOOL_DIRECTOR_PHONE, ""))
                .directorEmail(stored(SCHOOL_DIRECTOR_EMAIL, ""))
                .accreditationNumber(stored(SCHOOL_ACCREDITATION, ""))
                .additionalInfo(stored(SCHOOL_ADDITIONAL_INFO, ""))
                .logoUrl(stored(SCHOOL_LOGO, ""))
                .build();
    }

    /**
     * Valeur enregistrée telle quelle, même vide.
     *
     * <p>Différence avec {@link #value(String, String)} : {@code value()} traite
     * une valeur vide comme absente et renvoie le repli de configuration (utile
     * pour les en-têtes PDF). Le formulaire d'administration, lui, doit pouvoir
     * afficher un champ réellement vidé — sinon effacer l'e-mail ferait
     * réapparaître la valeur par défaut, ce qui laisserait croire que la
     * modification n'a pas été prise en compte.</p>
     */
    private String stored(String key, String fallback) {
        return settingRepository.findById(key)
                .map(Setting::getValue)
                .orElse(fallback);
    }

    /**
     * Enregistre les informations de l'établissement.
     *
     * <p>Le nom officiel est obligatoire ; tous les autres champs peuvent être
     * vides. Seules les clés du formulaire sont écrites — aucune autre
     * (cycles, WhatsApp activé…).</p>
     *
     * <p><b>Exception assumée</b> : l'indicatif téléphonique du pays est
     * enregistré sous la clé {@code WHATSAPP_DEFAULT_NUMBER}, qui est déjà celle
     * lue par {@link PhoneNumberService} pour tous les envois et par le lien
     * {@code wa.me}. Une clé distincte créerait deux valeurs concurrentes pour un
     * même réglage.</p>
     */
    @Transactional
    public com.school.dto.response.SchoolInfoResponse updateSchoolInfo(
            com.school.dto.request.SchoolInfoRequest request, HttpServletRequest httpRequest) {
        assertPhone(request.getPhone(), "principal");
        assertPhone(request.getPhoneAlt(), "secondaire");
        assertPhone(request.getDirectorPhone(), "du responsable");
        assertCountryCode(request.getPhoneCountryCode());
        if (request.getCreationYear() != null
                && request.getCreationYear() > java.time.Year.now().getValue() + 1) {
            throw new com.school.exception.BusinessException(
                    "L'année de création ne peut pas être dans le futur");
        }

        Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put(SCHOOL_NAME, text(request.getName()));
        values.put(SCHOOL_SHORT_NAME, text(request.getShortName()));
        values.put(SCHOOL_TYPE, text(request.getType()));
        values.put(SCHOOL_STATUS, text(request.getStatus()));
        values.put(SCHOOL_ID_NUMBER, text(request.getIdentificationNumber()));
        values.put(SCHOOL_SLOGAN, text(request.getMotto()));
        values.put(SCHOOL_CREATION_YEAR, request.getCreationYear() == null
                ? "" : String.valueOf(request.getCreationYear()));
        values.put(SCHOOL_ADDRESS, text(request.getAddress()));
        values.put(SCHOOL_DISTRICT, text(request.getDistrict()));
        values.put(SCHOOL_CITY, text(request.getCity()));
        values.put(SCHOOL_REGION, text(request.getRegion()));
        values.put(SCHOOL_COUNTRY, text(request.getCountry()));
        values.put(WHATSAPP_DEFAULT_NUMBER, countryCode(request.getPhoneCountryCode()));
        values.put(SCHOOL_PHONE, text(request.getPhone()));
        values.put(SCHOOL_PHONE_ALT, text(request.getPhoneAlt()));
        values.put(SCHOOL_EMAIL, text(request.getEmail()));
        values.put(SCHOOL_WEBSITE, text(request.getWebsite()));
        values.put(SCHOOL_DIRECTOR, text(request.getDirectorName()));
        values.put(SCHOOL_DIRECTOR_ROLE, text(request.getDirectorRole()));
        values.put(SCHOOL_DIRECTOR_PHONE, text(request.getDirectorPhone()));
        values.put(SCHOOL_DIRECTOR_EMAIL, text(request.getDirectorEmail()));
        values.put(SCHOOL_ACCREDITATION, text(request.getAccreditationNumber()));
        values.put(SCHOOL_ADDITIONAL_INFO, text(request.getAdditionalInfo()));

        updateAll(values, httpRequest);
        return schoolInfo();
    }

    /**
     * Supprime le logo de l'établissement : la référence est effacée en base
     * PUIS le fichier est supprimé. Un échec de suppression du fichier ne
     * laisse jamais la base pointer vers un logo supprimé.
     */
    @Transactional
    public void deleteLogo(HttpServletRequest httpRequest) {
        String current = value(SCHOOL_LOGO, "");
        updateAll(Map.of(SCHOOL_LOGO, ""), httpRequest);
        deleteLocalFile(current);
    }

    /** Supprime un fichier de {@code /uploads/...} sans jamais sortir du dossier. */
    private void deleteLocalFile(String publicPath) {        if (publicPath == null || !publicPath.startsWith("/uploads/")) {
            return;
        }
        try {
            String relative = publicPath.substring("/uploads/".length());
            java.nio.file.Path base = java.nio.file.Paths.get(appProperties.getUploadDir())
                    .toAbsolutePath().normalize();
            java.nio.file.Path file = base.resolve(relative).normalize();
            if (file.startsWith(base)) {
                java.nio.file.Files.deleteIfExists(file);
            }
        } catch (Exception e) {
            // Un logo orphelin est préférable à une suppression impossible :
            // la référence en base est déjà effacée.
        }
    }

    /**
     * Supprime l'ancien logo APRÈS que le nouveau a été enregistré en base.
     * Appelé par le contrôleur d'upload : si l'écriture échoue, l'ancien
     * fichier n'est jamais touché.
     */
    public void deleteReplacedLogo(String previousPublicPath) {
        deleteLocalFile(previousPublicPath);
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private Integer parseYear(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Indicatif pays normalisé : chiffres uniquement (« +237 », « 00237 » et
     * « 237 » donnent tous « 237 »). Une valeur vide efface le réglage, auquel
     * cas {@link PhoneNumberService} reprend l'indicatif de configuration.
     */
    private String countryCode(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private void assertCountryCode(String code) {
        if (code == null || code.isBlank()) {
            return;
        }
        String digits = code.replaceAll("\\D", "");
        if (digits.isEmpty() || digits.length() > 4) {
            throw new com.school.exception.BusinessException(
                    "L'indicatif du pays est invalide : 1 à 4 chiffres attendus (ex. 237)");
        }
    }

    /** Un téléphone doit contenir au moins 6 chiffres (formats Mali et internationaux). */
    private void assertPhone(String phone, String label) {
        if (phone == null || phone.isBlank()) {
            return;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 6 || digits.length() > 15) {
            throw new com.school.exception.BusinessException(
                    "Le numéro de téléphone " + label + " est invalide");
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    private List<String> parseCycles(String json) {
        try {
            return JSON.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of("JARDIN", "PRIMAIRE", "COLLEGE", "LYCEE", "UNIVERSITE");
        }
    }
}