package com.school.service;

import com.school.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Normalisation des numéros de téléphone au format international E.164.
 * <p>
 * L'indicatif pays est configurable via le paramètre WHATSAPP_DEFAULT_NUMBER
 * (ex. "223" pour le Mali, "237" pour le Cameroun), avec repli sur la
 * configuration applicative si le paramètre est absent.
 */
@Service
@RequiredArgsConstructor
public class PhoneNumberService {

    private static final String DEFAULT_COUNTRY_CODE = "237";

    private final SettingService settingService;
    private final AppProperties appProperties;

    /**
     * Normalise un numéro de téléphone en E.164, ou renvoie {@code null} si le
     * numéro est absent ou manifestement invalide.
     * <p>
     * Exemples (indicatif 223) :
     * <ul>
     *   <li>"76 12 34 56"          -> "+22376123456"</li>
     *   <li>"07 61 23 45 67"       -> "+223761234567"</li>
     *   <li>"+223 76 12 34 56"     -> "+22376123456"</li>
     *   <li>"223 76 12 34 56"      -> "+22376123456"</li>
     * </ul>
     */
    public String normalize(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String cleaned = phone.trim();
        String digits = cleaned.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        String countryCode = resolveCountryCode();

        String candidate;
        if (cleaned.startsWith("+")) {
            candidate = "+" + digits;
        } else if (digits.startsWith(countryCode)) {
            candidate = "+" + digits;
        } else if (digits.startsWith("0")) {
            candidate = "+" + countryCode + digits.substring(1);
        } else {
            candidate = "+" + countryCode + digits;
        }
        return isValidE164(candidate) ? candidate : null;
    }

    private String resolveCountryCode() {
        String fromSetting = settingService.value(SettingService.WHATSAPP_DEFAULT_NUMBER, "")
                .replaceAll("[^0-9]", "");
        if (!fromSetting.isEmpty()) {
            return fromSetting;
        }
        String fromConfig = appProperties.getWhatsapp() != null
                ? appProperties.getWhatsapp().getDefaultCountryCode() : null;
        if (fromConfig != null && !fromConfig.isBlank()) {
            return fromConfig.replaceAll("[^0-9]", "");
        }
        return DEFAULT_COUNTRY_CODE;
    }

    /**
     * E.164 : "+" suivi de 8 à 15 chiffres.
     */
    private boolean isValidE164(String number) {
        String digits = number.replaceAll("[^0-9]", "");
        return digits.length() >= 8 && digits.length() <= 15;
    }
}
