package com.school.controller;

import com.school.dto.request.AdmissionEnrollmentRequest;
import com.school.dto.request.CandidatureRequest;
import com.school.dto.response.AdmissionEnrollmentResponse;
import com.school.dto.response.ApiResponse;
import com.school.dto.response.CandidatureResponse;
import com.school.dto.response.PageResponse;
import com.school.enums.CandidatureStatus;
import com.school.service.CandidatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Module admission : candidatures, dossiers, sélection.
 */
@RestController
@RequestMapping("/api/candidatures")
@RequiredArgsConstructor
@Validated
@Tag(name = "Admission", description = "Candidatures d'admission")
public class CandidatureController {

    private final CandidatureService candidatureService;

    @GetMapping
    @Operation(summary = "Liste paginée des candidatures")
    public ResponseEntity<ApiResponse<PageResponse<CandidatureResponse>>> list(
            @RequestParam(required = false) String fieldName,
            @RequestParam(required = false) CandidatureStatus status,
            @Min(value = 0, message = "Le numéro de page ne peut pas être négatif") @RequestParam(defaultValue = "0") int page,
            @Max(value = 100, message = "La taille de page ne peut pas dépasser 100")
            @RequestParam(defaultValue = "10") int size) {
        return ok("Candidatures", PageResponse.from(candidatureService.list(fieldName, status, page, size), x -> x));
    }

    @PostMapping
    @Operation(summary = "Déposer une candidature")
    public ResponseEntity<ApiResponse<CandidatureResponse>> create(@Valid @RequestBody CandidatureRequest request,
                                                                   HttpServletRequest httpRequest) {
        return ok("Candidature déposée", candidatureService.create(request, httpRequest));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Mettre à jour le statut (sélection, liste d'attente, refus)")
    public ResponseEntity<ApiResponse<CandidatureResponse>> updateStatus(@PathVariable Long id,
                                                                         @RequestParam CandidatureStatus status,
                                                                         HttpServletRequest httpRequest) {
        return ok("Statut mis à jour", candidatureService.updateStatus(id, status, httpRequest));
    }

    @PostMapping("/{id}/enroll")
    @Operation(summary = "Inscrire une candidature acceptée : crée l'élève et le rattache")
    public ResponseEntity<ApiResponse<AdmissionEnrollmentResponse>> enroll(
            @PathVariable Long id,
            @Valid @RequestBody AdmissionEnrollmentRequest request,
            HttpServletRequest httpRequest) {
        return ok("Candidature inscrite", candidatureService.enroll(id, request, httpRequest));
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
