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

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    private List<String> parseCycles(String json) {
        try {
            return JSON.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of("JARDIN", "PRIMAIRE", "COLLEGE", "LYCEE", "UNIVERSITE");
        }
    }
}