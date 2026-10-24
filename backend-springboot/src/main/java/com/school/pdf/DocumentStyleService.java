package com.school.pdf;

import com.school.entity.DocumentStyleOverride;
import com.school.entity.DocumentThemeVersion;
import com.school.exception.BusinessException;
import com.school.repository.DocumentStyleOverrideRepository;
import com.school.repository.DocumentThemeVersionRepository;
import com.school.service.AuditService;
import com.school.utils.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service central de résolution de la configuration documentaire.
 *
 * <p>C'est le point d'entrée unique que tous les générateurs PDF interrogent.
 * Il combine deux niveaux, du plus général au plus précis :</p>
 * <ol>
 *   <li><b>Thème actif</b> — la version enregistrée et activée
 *       ({@code document_theme_versions.active = 1}) ;</li>
 *   <li><b>Surcharges ponctuelles par type de document</b> —
 *       {@code document_style_overrides}, fusionnés clé par clé sur le thème.</li>
 * </ol>
 *
 * <p>Il n'existe volontairement <b>qu'un seul magasin de thèmes</b> : une
 * seconde table de styles complets aurait rendu ambiguë la question « quelle
 * est la couleur réellement utilisée ? ». Une option de niveau 2 ne remplace
 * donc jamais la configuration, elle n'en modifie que les clés citées.</p>
 *
 * <p>Aucune configuration enregistrée ⇒ {@link DocumentStyle#defaults()},
 * c'est-à-dire exactement la charte historique codée en dur. Un changement de
 * thème ne modifie donc jamais l'apparence par accident.</p>
 *
 * <p>La version active est mise en cache en mémoire (invalidée à chaque
 * enregistrement) : une génération de PDF n'ajoute pas de requête SQL pour
 * relire la configuration.</p>
 */
@Service
@RequiredArgsConstructor
public class DocumentStyleService {

    private final DocumentThemeVersionRepository versionRepository;
    private final DocumentStyleOverrideRepository overrideRepository;
    private final AuditService auditService;

    // Cache de la version active : null = pas encore lu.
    private volatile CachedTheme cached;
    private volatile boolean cacheLoaded;

    private record CachedTheme(DocumentStyle style, Long versionId, String label) {
    }

    // ==================================================================
    // Résolution (lecture, utilisée par les générateurs)
    // ==================================================================

    /** Thème actif (jamais null). */
    @Transactional(readOnly = true)
    public DocumentStyle activeStyle() {
        return activeTheme().style();
    }

    /** Identifiant de la version active, {@code null} si aucune n'est activée. */
    @Transactional(readOnly = true)
    public Long activeVersionId() {
        return activeTheme().versionId();
    }

    /** Libellé de la version active, {@code null} si aucune n'est activée. */
    @Transactional(readOnly = true)
    public String activeVersionLabel() {
        return activeTheme().label();
    }

    /**
     * Configuration effective pour un type de document : thème actif, puis
     * modèle complet du type, puis surcharges ponctuelles.
     *
     * @param documentType code de {@link DocumentTypes}, ou {@code null} pour
     *                     obtenir simplement le thème global
     */
    @Transactional(readOnly = true)
    public DocumentStyle resolve(String documentType) {
        DocumentStyle style = activeStyle();
        if (documentType == null || documentType.isBlank()) {
            return style;
        }
        String type = documentType.toUpperCase(java.util.Locale.ROOT);

        Optional<DocumentStyleOverride> partial =
                overrideRepository.findByDocumentType(type);
        if (partial.isPresent()) {
            JsonNode node = DocumentStyleCodec.parse(partial.get().getConfigJson());
            if (node != null) {
                return applyOverrides(style, node);
            }
        }
        return style;
    }

    /**
     * Applique des surcharges partielles sur un thème : seules les clés
     * présentes dans {@code patch} changent, tout le reste est conservé.
     */
    static DocumentStyle applyOverrides(DocumentStyle base, JsonNode patch) {
        if (patch == null || !patch.isObject() || patch.isEmpty()) {
            return base;
        }
        ObjectNode merged = DocumentStyleCodec.encode(base);
        deepMerge(merged, patch);
        return DocumentStyleCodec.decode(merged);
    }

    private static void deepMerge(ObjectNode target, JsonNode patch) {
        patch.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                return;
            }
            JsonNode existing = target.get(key);
            if (existing != null && existing.isObject() && value.isObject()) {
                deepMerge((ObjectNode) existing, value);
            } else {
                target.set(key, value);
            }
        });
    }

    /**
     * Style d'une version donnée, indépendamment de la version active.
     * Sert à l'écran d'historique : revoir une configuration archivée ne la
     * réactive pas.
     */
    @Transactional(readOnly = true)
    public DocumentStyle styleForVersion(Long id) {
        return versionRepository.findById(id)
                .map(v -> DocumentStyleCodec.decode(DocumentStyleCodec.parse(v.getConfigJson())))
                .orElseThrow(() -> new com.school.exception.ResourceNotFoundException(
                        "Configuration de document introuvable"));
    }

    /** Invalide le cache : à appeler après toute écriture. */
    public void invalidateCache() {
        cacheLoaded = false;
        cached = null;
    }

    private CachedTheme activeTheme() {
        if (cacheLoaded) {
            CachedTheme c = cached;
            return c != null ? c : new CachedTheme(DocumentStyle.defaults(), null, null);
        }
        Optional<DocumentThemeVersion> active = versionRepository.findFirstByActiveTrueOrderByIdDesc();
        CachedTheme resolved = active
                .map(v -> new CachedTheme(DocumentStyleCodec.decode(
                        DocumentStyleCodec.parse(v.getConfigJson())), v.getId(), v.getLabel()))
                .orElseGet(() -> new CachedTheme(DocumentStyle.defaults(), null, null));
        cached = resolved;
        cacheLoaded = true;
        return resolved;
    }

    // ==================================================================
    // Gestion des versions (écriture, réservée à l'administrateur)
    // ==================================================================

    /** Historique, du plus récent au plus ancien. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> history() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (DocumentThemeVersion v : versionRepository.findAllByOrderByIdDesc()) {
            out.add(describeVersion(v));
        }
        return out;
    }

    public static Map<String, Object> describeVersion(DocumentThemeVersion v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("label", v.getLabel());
        m.put("template", v.getTemplate());
        m.put("palette", v.getPalette());
        m.put("active", v.isActive());
        m.put("createdAt", v.getCreatedAt());
        m.put("createdBy", v.getCreatedBy());
        return m;
    }

    /**
     * Enregistre une configuration. Si {@code activate} est vrai, elle devient
     * le thème actif et l'ancienne est simplement désactivée — jamais
     * supprimée, pour permettre le retour arrière.
     */
    @Transactional
    public DocumentThemeVersion save(String label, JsonNode config, boolean activate,
                                     HttpServletRequest request) {
        DocumentStyleCodec.validate(config);
        DocumentStyle style = DocumentStyleCodec.decode(config);
        JsonNode normalised = DocumentStyleCodec.encode(style);

        String safeLabel = label == null || label.isBlank()
                ? "Configuration du " + LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                : label.trim();
        if (safeLabel.length() > 120) {
            throw new BusinessException("Le nom de la configuration ne peut pas dépasser 120 caractères");
        }

        DocumentThemeVersion version = DocumentThemeVersion.builder()
                .label(safeLabel)
                .template(style.template.name())
                .palette(config.hasNonNull("palette") ? config.get("palette").asText() : null)
                .configJson(DocumentStyleCodec.stringify(normalised))
                .active(false)
                .createdAt(LocalDateTime.now())
                .createdBy(SecurityUtils.currentUsername())
                .build();
        version = versionRepository.save(version);

        if (activate) {
            activateVersion(version.getId(), request);
        } else {
            invalidateCache();
            auditService.log("DOCUMENT_THEME_SAVE", "DocumentTheme", version.getId(),
                    "Configuration enregistrée : " + safeLabel, request);
        }
        return version;
    }

    /** Active une version existante (aucune écriture de contenu). */
    @Transactional
    public DocumentThemeVersion activateVersion(Long id, HttpServletRequest request) {
        DocumentThemeVersion version = versionRepository.findById(id)
                .orElseThrow(() -> new com.school.exception.ResourceNotFoundException(
                        "Configuration de document introuvable"));
        versionRepository.deactivateAll();
        version.setActive(true);
        versionRepository.save(version);
        invalidateCache();
        auditService.log("DOCUMENT_THEME_ACTIVATE", "DocumentTheme", id,
                "Thème actif : " + version.getLabel(), request);
        return version;
    }

    /**
     * Désactive la configuration : l'application revient à la charte d'origine.
     * Les versions enregistrées sont conservées (réactivables).
     */
    @Transactional
    public void restoreDefault(HttpServletRequest request) {
        versionRepository.deactivateAll();
        invalidateCache();
        auditService.log("DOCUMENT_THEME_RESET", "DocumentTheme", null,
                "Retour à la charte d'origine", request);
    }

    // ==================================================================
    // Niveau 2 : options par type de document
    // ==================================================================

    @Transactional(readOnly = true)
    public Map<String, String> overridesByType() {
        Map<String, String> m = new LinkedHashMap<>();
        for (DocumentStyleOverride o : overrideRepository.findAll()) {
            m.put(o.getDocumentType(), o.getConfigJson());
        }
        return m;
    }

    /** Enregistre (ou supprime si {@code config} est vide) la surcharge d'un type. */
    @Transactional
    public void saveOverride(String documentType, JsonNode config, HttpServletRequest request) {
        String type = requireKnownType(documentType);
        if (config == null || config.isEmpty()) {
            overrideRepository.deleteByDocumentType(type);
            auditService.log("DOCUMENT_STYLE_OVERRIDE_DELETE", "DocumentStyle", null,
                    "Options spécifiques supprimées : " + type, request);
        } else {
            DocumentStyleCodec.validate(config);
            DocumentStyleOverride row = overrideRepository.findByDocumentType(type)
                    .orElseGet(() -> DocumentStyleOverride.builder().documentType(type).build());
            row.setConfigJson(DocumentStyleCodec.stringify(config));
            row.setUpdatedAt(LocalDateTime.now());
            overrideRepository.save(row);
            auditService.log("DOCUMENT_STYLE_OVERRIDE_SAVE", "DocumentStyle", row.getId(),
                    "Options spécifiques enregistrées : " + type, request);
        }
        invalidateCache();
    }

    private String requireKnownType(String documentType) {
        String type = documentType == null ? "" : documentType.trim().toUpperCase(java.util.Locale.ROOT);
        if (!DocumentTypes.CODES.contains(type)) {
            throw new BusinessException("Type de document inconnu : " + documentType);
        }
        return type;
    }
}
