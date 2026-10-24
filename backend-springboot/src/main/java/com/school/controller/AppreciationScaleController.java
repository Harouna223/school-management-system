package com.school.controller;

import com.school.dto.request.AppreciationScaleRequest;
import com.school.dto.response.ApiResponse;
import com.school.dto.response.AppreciationPreviewResponse;
import com.school.dto.response.AppreciationScaleResponse;
import com.school.enums.EducationCycle;
import com.school.service.AppreciationScaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Configuration des barèmes d'appréciation par cycle scolaire.
 *
 * <p>Réservé aux administrateurs autorisés : lecture par
 * {@code PERM_APPRECIATION_READ}, écriture par {@code PERM_APPRECIATION_WRITE}
 * (voir SecurityConfig).</p>
 */
@RestController
@RequestMapping("/api/appreciation-scales")
@RequiredArgsConstructor
@Validated
@Tag(name = "Barèmes d'appréciation", description = "Appréciations configurables par cycle")
public class AppreciationScaleController {

    private final AppreciationScaleService appreciationScaleService;

    @GetMapping
    @Operation(summary = "Lister les barèmes (filtres cycle et statut)")
    public ResponseEntity<ApiResponse<List<AppreciationScaleResponse>>> list(
            @RequestParam(required = false) EducationCycle cycle,
            @RequestParam(required = false) Boolean active) {
        return ok("Barèmes d'appréciation", appreciationScaleService.list(cycle, active));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un barème et de ses intervalles")
    public ResponseEntity<ApiResponse<AppreciationScaleResponse>> get(@PathVariable Long id) {
        return ok("Barème", appreciationScaleService.get(id));
    }

    @PostMapping
    @Operation(summary = "Créer un barème (inactif tant qu'il n'est pas activé)")
    public ResponseEntity<ApiResponse<AppreciationScaleResponse>> create(
            @Valid @RequestBody AppreciationScaleRequest request, HttpServletRequest httpRequest) {
        return ok("Barème créé", appreciationScaleService.create(request, httpRequest));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un barème (nouvelle version s'il est déjà utilisé)")
    public ResponseEntity<ApiResponse<AppreciationScaleResponse>> update(
            @PathVariable Long id, @Valid @RequestBody AppreciationScaleRequest request,
            HttpServletRequest httpRequest) {
        return ok("Barème modifié", appreciationScaleService.update(id, request, httpRequest));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activer un barème (un seul actif par cycle)")
    public ResponseEntity<ApiResponse<AppreciationScaleResponse>> activate(
            @PathVariable Long id, HttpServletRequest httpRequest) {
        return ok("Barème activé", appreciationScaleService.activate(id, httpRequest));
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Désactiver un barème")
    public ResponseEntity<ApiResponse<AppreciationScaleResponse>> deactivate(
            @PathVariable Long id, HttpServletRequest httpRequest) {
        return ok("Barème désactivé", appreciationScaleService.deactivate(id, httpRequest));
    }

    @PostMapping("/{id}/duplicate")
    @Operation(summary = "Dupliquer un barème en brouillon")
    public ResponseEntity<ApiResponse<AppreciationScaleResponse>> duplicate(
            @PathVariable Long id, HttpServletRequest httpRequest) {
        return ok("Barème dupliqué", appreciationScaleService.duplicate(id, httpRequest));
    }

    @PostMapping("/preview")
    @Operation(summary = "Prévisualiser un barème avant enregistrement")
    public ResponseEntity<ApiResponse<AppreciationPreviewResponse>> preview(
            @Valid @RequestBody AppreciationScaleRequest request) {
        return ok("Prévisualisation", appreciationScaleService.preview(request));
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}
