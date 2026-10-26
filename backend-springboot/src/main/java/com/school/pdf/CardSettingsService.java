package com.school.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.school.service.AuditService;
import com.school.service.FileStorageService;
import com.school.service.SettingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Réglages de la carte scolaire : configuration graphique et emblème.
 *
 * <p>Stockage dans la table {@code settings}, comme le reste de l'identité de
 * l'établissement — aucune table ni migration supplémentaire :</p>
 * <ul>
 *   <li>{@code SCHOOL_CARD_CONFIG} — la configuration complète en JSON
 *       (colonne {@code TEXT}), écrite par {@link CardConfigCodec} ;</li>
 *   <li>{@code SCHOOL_CARD_EMBLEM} — le chemin public de l'emblème téléversé
 *       ({@code /uploads/school/…}).</li>
 * </ul>
 *
 * <p><b>Pourquoi l'emblème est distinct du logo</b> : le logo est l'identité
 * commerciale de l'établissement et sert d'en-tête à <i>tous</i> les documents ;
 * l'emblème de la carte est un choix graphique propre au document (armoiries,
 * écusson, ou le logo lui-même). Les confondre obligerait à changer tous les
 * bulletins pour retoucher la carte.</p>
 *
 * <p>Aucune valeur n'est écrite si la validation échoue : une configuration
 * refusée ne remplace jamais celle qui fonctionne.</p>
 */
@Service
@RequiredArgsConstructor
public class CardSettingsService {

    /** Clé de la configuration graphique de la carte. */
    public static final String CONFIG_KEY = "SCHOOL_CARD_CONFIG";

    /** Clé du chemin public de l'emblème de la carte. */
    public static final String EMBLEM_KEY = "SCHOOL_CARD_EMBLEM";

    private final SettingService settingService;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    /** Configuration effective ; les valeurs d'origine si rien n'est enregistré. */
    @Transactional(readOnly = true)
    public CardConfig config() {
        return CardConfigCodec.decode(CardConfigCodec.parse(rawJson()));
    }

    /** JSON enregistré, ou {@code null} si l'administrateur n'a rien réglé. */
    @Transactional(readOnly = true)
    public JsonNode raw() {
        return CardConfigCodec.parse(rawJson());
    }

    /** Vrai si une configuration a été enregistrée (même identique aux valeurs d'origine). */
    @Transactional(readOnly = true)
    public boolean isCustomised() {
        String stored = rawJson();
        return stored != null && !stored.isBlank();
    }

    /** Chemin public de l'emblème, {@code ""} s'il n'y en a pas. */
    @Transactional(readOnly = true)
    public String emblemUrl() {
        return settingService.value(EMBLEM_KEY, "");
    }

    /** Fichier de l'emblème, vide s'il n'existe pas ou sort du dossier d'uploads. */
    public Optional<Path> emblemFile() {
        return settingService.resolveUploadedFile(emblemUrl());
    }

    // ------------------------------------------------------------------
    // Écriture
    // ------------------------------------------------------------------

    /**
     * Enregistre la configuration après validation.
     *
     * <p>Le JSON est réencodé depuis l'objet décodé : les clés inconnues
     * envoyées par le client ne sont jamais persistées, et les valeurs
     * numériques sont ramenées dans leurs bornes. Ce qui est stocké est donc
     * exactement ce qui sera rendu.</p>
     */
    @Transactional
    public CardConfig update(JsonNode config, HttpServletRequest request) {
        CardConfigCodec.validate(config);
        CardConfig normalised = CardConfigCodec.decode(config);
        JsonNode stored = CardConfigCodec.encode(normalised);
        settingService.updateAll(Map.of(CONFIG_KEY, CardConfigCodec.stringify(stored)), request);
        return normalised;
    }

    /**
     * Retire la configuration : la carte revient au modèle d'origine.
     *
     * <p>L'emblème téléversé est conservé — il peut resservir si
     * l'administrateur réactive un réglage qui l'utilise. Seul le réglage
     * « provenance de l'emblème » est réinitialisé, et il vaut « logo de
     * l'école » par défaut : la carte reste donc complète.</p>
     */
    @Transactional
    public void reset(HttpServletRequest request) {
        settingService.updateAll(Map.of(CONFIG_KEY, ""), request);
        auditService.log("SCHOOL_CARD_RESET", "Setting", null,
                "Réglages de la carte scolaire réinitialisés", request);
    }

    /**
     * Téléverse l'emblème de la carte.
     *
     * <p>Même ordre que pour le logo : le nouveau fichier est écrit et
     * référencé <b>avant</b> que l'ancien soit supprimé. Un téléversement
     * interrompu ne laisse jamais la carte sans image.</p>
     *
     * @return le chemin public du nouvel emblème
     */
    @Transactional
    public String storeEmblem(MultipartFile file, HttpServletRequest request) {
        String previous = emblemUrl();
        String url = fileStorageService.store(file, "school");
        if (url == null || url.isBlank()) {
            throw new com.school.exception.BusinessException("Aucun fichier reçu");
        }
        settingService.updateAll(Map.of(EMBLEM_KEY, url), request);
        if (previous != null && !previous.isBlank() && !previous.equals(url)) {
            settingService.deleteUploadedFile(previous);
        }
        return url;
    }

    /**
     * Supprime l'emblème : la référence est effacée en base <b>puis</b> le
     * fichier, pour que la base ne pointe jamais vers un fichier absent.
     */
    @Transactional
    public void deleteEmblem(HttpServletRequest request) {
        String current = emblemUrl();
        settingService.updateAll(Map.of(EMBLEM_KEY, ""), request);
        settingService.deleteUploadedFile(current);
    }

    private String rawJson() {
        return settingService.value(CONFIG_KEY, "");
    }
}
