package com.school.controller;

import com.school.dto.response.ApiResponse;
import com.school.pdf.DocumentStyle;
import com.school.pdf.DocumentStyleCodec;
import com.school.pdf.DocumentPreviewService;
import com.school.pdf.DocumentStyleService;
import com.school.pdf.DocumentThemePresets;
import com.school.pdf.DocumentTypes;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modèles et apparence des documents.
 *
 * <p>Réservé à l'administrateur : la règle {@code /api/settings/** →
 * hasRole('SUPER_ADMIN')} de {@link com.school.security.SecurityConfig}
 * s'applique à toutes ces routes, y compris les prévisualisations (qui
 * produisent des PDF à partir de données d'exemple, jamais des données
 * réelles).</p>
 */
@RestController
@RequestMapping("/api/settings/document-theme")
@RequiredArgsConstructor
@Tag(name = "Modèles de documents",
        description = "Personnalisation de l'apparence de tous les documents PDF")
public class DocumentThemeController {

    private final DocumentStyleService documentStyleService;
    private final DocumentTypes documentTypes;
    private final DocumentPreviewService previewService;

    // ------------------------------------------------------------------
    // Catalogue et état courant
    // ------------------------------------------------------------------

    @GetMapping
    @Operation(summary = "Configuration active, catalogue et préréglages")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get() {
        DocumentStyle active = documentStyleService.activeStyle();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("active", DocumentStyleCodec.describe(active));
        data.put("activeVersionId", documentStyleService.activeVersionId());
        data.put("activeVersionLabel", documentStyleService.activeVersionLabel());
        data.put("usingDefaults", documentStyleService.activeVersionId() == null);
        data.put("templates", DocumentThemePresets.TEMPLATES.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", t.code());
            m.put("label", t.label());
            m.put("description", t.description());
            return m;
        }).toList());
        data.put("palettes", DocumentThemePresets.PALETTES.stream()
                .map(DocumentThemePresets::paletteAsMap).toList());
        data.put("documents", documentTypes.catalogue());
        data.put("contrastWarnings", DocumentStyleCodec.contrastWarnings(active));
        return ok("Configuration active", data);
    }

    @GetMapping("/defaults")
    @Operation(summary = "Charte d'origine (valeurs codées en dur)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> defaults() {
        DocumentStyle d = DocumentStyle.defaults();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("config", DocumentStyleCodec.describe(d));
        data.put("json", DocumentStyleCodec.encode(d));
        return ok("Charte d'origine", data);
    }

    @GetMapping("/presets/{template}")
    @Operation(summary = "Apparence résultante d'un modèle (sur le thème actif)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> preset(@PathVariable String template) {
        if (!DocumentStyleCodec.isTemplate(template)) {
            throw new com.school.exception.BusinessException(
                    "Modèle de document inconnu : " + template);
        }
        DocumentStyle style = DocumentThemePresets.applyTemplate(
                template, documentStyleService.activeStyle());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("config", DocumentStyleCodec.describe(style));
        data.put("json", DocumentStyleCodec.encode(style));
        data.put("contrastWarnings", DocumentStyleCodec.contrastWarnings(style));
        return ok("Modèle " + template, data);
    }

    @PostMapping("/preview-config")
    @Operation(summary = "Diagnostic d'une configuration non enregistrée")
    public ResponseEntity<ApiResponse<Map<String, Object>>> previewConfig(@RequestBody JsonNode config) {
        DocumentStyleCodec.validate(config);
        DocumentStyle style = DocumentStyleCodec.decode(config);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("config", DocumentStyleCodec.describe(style));
        data.put("contrastWarnings", DocumentStyleCodec.contrastWarnings(style));
        return ok("Configuration analysée", data);
    }

    // ------------------------------------------------------------------
    // Enregistrement / activation / retour arrière
    // ------------------------------------------------------------------

    /**
     * Enregistre une configuration.
     *
     * <p>{@code activate=false} permet d'enregistrer sans appliquer : utile pour
     * préparer un thème hors ligne. Dans tous les cas une nouvelle version est
     * créée — l'ancienne reste intacte et réactivable.</p>
     */
    @PostMapping
    @Operation(summary = "Enregistrer une configuration")
    public ResponseEntity<ApiResponse<Map<String, Object>>> save(
            @RequestBody Map<String, Object> body, HttpServletRequest httpRequest) {
        Object label = body.get("label");
        boolean activate = Boolean.TRUE.equals(body.get("activate"));
        JsonNode config = asJson(body.get("config"));
        var version = documentStyleService.save(
                label == null ? null : label.toString(), config, activate, httpRequest);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", DocumentStyleService.describeVersion(version));
        data.put("active", DocumentStyleCodec.describe(documentStyleService.activeStyle()));
        return ok(activate ? "Configuration appliquée" : "Configuration enregistrée", data);
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Réactiver une configuration de l'historique")
    public ResponseEntity<ApiResponse<Map<String, Object>>> activate(@PathVariable Long id,
                                                                    HttpServletRequest httpRequest) {
        var version = documentStyleService.activateVersion(id, httpRequest);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", DocumentStyleService.describeVersion(version));
        data.put("active", DocumentStyleCodec.describe(documentStyleService.activeStyle()));
        return ok("Configuration réactivée", data);
    }

    @DeleteMapping
    @Operation(summary = "Restaurer la charte d'origine")
    public ResponseEntity<ApiResponse<Map<String, Object>>> restoreDefault(HttpServletRequest httpRequest) {
        documentStyleService.restoreDefault(httpRequest);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("active", DocumentStyleCodec.describe(documentStyleService.activeStyle()));
        data.put("usingDefaults", true);
        return ok("Charte d'origine restaurée", data);
    }

    @GetMapping("/history")
    @Operation(summary = "Historique des configurations")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> history() {
        return ok("Historique", documentStyleService.history());
    }

    @GetMapping("/history/{id}")
    @Operation(summary = "Détail d'une configuration enregistrée")
    public ResponseEntity<ApiResponse<Map<String, Object>>> historyDetail(@PathVariable Long id) {
        var version = documentStyleService.history().stream()
                .filter(v -> id.equals(v.get("id")))
                .findFirst()
                .orElseThrow(() -> new com.school.exception.ResourceNotFoundException(
                        "Configuration de document introuvable"));
        DocumentStyle style = documentStyleService.styleForVersion(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", version);
        data.put("config", DocumentStyleCodec.describe(style));
        data.put("json", DocumentStyleCodec.encode(style));
        return ok("Configuration", data);
    }

    // ------------------------------------------------------------------
    // Options par type de document (niveau 2)
    // ------------------------------------------------------------------

    @GetMapping("/overrides")
    @Operation(summary = "Options spécifiques par type de document")
    public ResponseEntity<ApiResponse<Map<String, String>>> overrides() {
        return ok("Options spécifiques", documentStyleService.overridesByType());
    }

    @PutMapping("/overrides/{documentType}")
    @Operation(summary = "Enregistrer les options d'un type de document")
    public ResponseEntity<ApiResponse<Map<String, String>>> saveOverride(
            @PathVariable String documentType, @RequestBody JsonNode config,
            HttpServletRequest httpRequest) {
        documentStyleService.saveOverride(documentType, config, httpRequest);
        return ok("Options enregistrées", documentStyleService.overridesByType());
    }

    @DeleteMapping("/overrides/{documentType}")
    @Operation(summary = "Supprimer les options d'un type de document")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteOverride(
            @PathVariable String documentType, HttpServletRequest httpRequest) {
        documentStyleService.saveOverride(documentType, null, httpRequest);
        return ok("Options supprimées", documentStyleService.overridesByType());
    }

    // ------------------------------------------------------------------
    // Prévisualisation
    // ------------------------------------------------------------------

    /**
     * PDF d'aperçu.
     *
     * <p>Le corps est la configuration <b>non enregistrée</b> éventuellement
     * modifiée dans l'écran ; absent, la configuration active est utilisée.
     * Les données du document sont fictives et signalées comme telles.</p>
     */
    @PostMapping("/preview")
    @Operation(summary = "Prévisualiser un document avec une configuration")
    public void preview(@RequestParam(required = false) String documentType,
                        @RequestBody(required = false) JsonNode config,
                        jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        DocumentStyle style;
        if (config == null || config.isNull() || config.isEmpty()) {
            style = documentStyleService.resolve(documentType);
        } else {
            DocumentStyleCodec.validate(config);
            style = DocumentStyleCodec.decode(config);
        }
        previewService.preview(response, documentType, style);
    }

    // ------------------------------------------------------------------

    private JsonNode asJson(Object raw) {
        if (raw instanceof JsonNode n) {
            return n;
        }
        if (raw == null) {
            throw new com.school.exception.BusinessException("Configuration de document manquante");
        }
        // Sérialise puis relit : garantit que l'objet reçu est bien du JSON
        // et non une structure libre acceptée par erreur.
        return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(raw);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}
