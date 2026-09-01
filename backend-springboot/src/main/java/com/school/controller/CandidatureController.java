package com.school.controller;

import com.school.dto.response.ApiResponse;
import com.school.dto.response.PageResponse;
import com.school.entity.Candidature;
import com.school.enums.CandidatureStatus;
import com.school.service.CandidatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Module admission universitaire : candidatures, dossiers, sélection.
 */
@RestController
@RequestMapping("/api/candidatures")
@RequiredArgsConstructor
@Tag(name = "Admission", description = "Candidatures d'admission universitaire")
public class CandidatureController {

    private final CandidatureService candidatureService;

    @GetMapping
    @Operation(summary = "Liste paginée des candidatures")
    public ResponseEntity<ApiResponse<PageResponse<Candidature>>> list(
            @RequestParam(required = false) Long fieldId,
            @RequestParam(required = false) CandidatureStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ok("Candidatures", PageResponse.from(candidatureService.list(fieldId, status, page, size), x -> x));
    }

    @PostMapping
    @Operation(summary = "Déposer une candidature")
    public ResponseEntity<ApiResponse<Candidature>> create(@RequestBody Candidature candidature,
                                                           HttpServletRequest httpRequest) {
        return ok("Candidature déposée", candidatureService.create(candidature, httpRequest));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Mettre à jour le statut (sélection, liste d'attente, refus)")
    public ResponseEntity<ApiResponse<Candidature>> updateStatus(@PathVariable Long id,
                                                                 @RequestParam CandidatureStatus status,
                                                                 HttpServletRequest httpRequest) {
        return ok("Statut mis à jour", candidatureService.updateStatus(id, status, httpRequest));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer une candidature")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        candidatureService.delete(id, httpRequest);
        return ok("Candidature supprimée", null);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}