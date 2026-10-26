package com.school.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.school.dto.response.ApiResponse;
import com.school.pdf.CardConfig;
import com.school.pdf.CardConfigCodec;
import com.school.pdf.CardSettingsService;
import com.school.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Réglages de la <b>carte scolaire</b> : emblème, logo, textes, couleurs,
 * champs imprimés, et aperçu du document réel.
 *
 * <p>Réservé à l'administrateur : la règle {@code /api/settings/** →
 * hasRole('SUPER_ADMIN')} de {@link com.school.security.SecurityConfig}
 * s'applique à toutes ces routes, aperçu compris.</p>
 *
 * <p>Le téléchargement du logo de l'établissement, lui, reste sur
 * {@link SettingController} ({@code GET /api/settings/logo/download}) : c'est
 * la même ressource que celle affichée en tête de tous les documents, pas un
 * réglage de la carte.</p>
 */
@RestController
@RequestMapping("/api/settings/card")
@RequiredArgsConstructor
@Tag(name = "Carte scolaire", description = "Personnalisation de la carte d'identité scolaire")
public class StudentCardController {

    private final CardSettingsService cardSettingsService;
    private final ReportService reportService;

    // ------------------------------------------------------------------
    // État et catalogue
    // ------------------------------------------------------------------

    @GetMapping
    @Operation(summary = "Réglages de la carte, catalogue des champs et provenance de l'emblème")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get() {
        CardConfig config = cardSettingsService.config();
        Map<String, Object> data = new LinkedHashMap<>(CardConfigCodec.describe(config));
        data.put("usingDefaults", !cardSettingsService.isCustomised());
        data.put("emblemUrl", cardSettingsService.emblemUrl());
        data.put("emblemAvailable", cardSettingsService.emblemFile().isPresent());
        return ok("Réglages de la carte", data);
    }

    @GetMapping("/defaults")
    @Operation(summary = "Modèle d'origine de la carte (valeurs codées en dur)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> defaults() {
        return ok("Modèle d'origine", CardConfigCodec.describe(CardConfig.defaults()));
    }

    // ------------------------------------------------------------------
    // Écriture
    // ------------------------------------------------------------------

    @PutMapping
    @Operation(summary = "Enregistrer les réglages de la carte")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(
            @RequestBody JsonNode config, HttpServletRequest httpRequest) {
        cardSettingsService.update(config, httpRequest);
        return get();
    }

    @DeleteMapping
    @Operation(summary = "Revenir au modèle d'origine")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reset(HttpServletRequest httpRequest) {
        cardSettingsService.reset(httpRequest);
        return get();
    }

    @PostMapping(value = "/emblem", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Téléverser l'emblème de la carte")
    public ResponseEntity<ApiResponse<String>> uploadEmblem(@RequestPart("file") MultipartFile file,
                                                            HttpServletRequest httpRequest) {
        return ok("Emblème mis à jour", cardSettingsService.storeEmblem(file, httpRequest));
    }

    @DeleteMapping("/emblem")
    @Operation(summary = "Supprimer l'emblème de la carte")
    public ResponseEntity<ApiResponse<Void>> deleteEmblem(HttpServletRequest httpRequest) {
        cardSettingsService.deleteEmblem(httpRequest);
        return ok("Emblème supprimé", null);
    }

    // ------------------------------------------------------------------
    // Aperçu
    // ------------------------------------------------------------------

    /**
     * PDF de la carte <b>réelle</b>, avec la configuration fournie.
     *
     * <p>Le corps est la configuration <b>non enregistrée</b> telle qu'affichée
     * dans l'écran de réglage : l'administrateur voit donc exactement ce qu'il
     * obtiendra avant d'enregistrer. Absent, la configuration active est
     * utilisée.</p>
     *
     * <p>Les données d'identité sont fictives et le document porte la mention
     * « APERÇU — SANS VALEUR OFFICIELLE ».</p>
     */
    @PostMapping("/preview")
    @Operation(summary = "Prévisualiser la carte avec des réglages")
    public void preview(@RequestBody(required = false) JsonNode config,
                        jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        CardConfig effective;
        if (config == null || config.isNull() || config.isEmpty()) {
            effective = cardSettingsService.config();
        } else {
            CardConfigCodec.validate(config);
            effective = CardConfigCodec.decode(config);
        }
        reportService.studentCardPreviewPdf(response, effective);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}
