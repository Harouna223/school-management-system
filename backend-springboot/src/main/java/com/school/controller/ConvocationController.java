package com.school.controller;

import com.school.dto.request.ConvocationRequest;
import com.school.dto.response.ApiResponse;
import com.school.dto.response.ConvocationResponse;
import com.school.service.AccessControlService;
import com.school.service.ConvocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Module convocations : scolaires et universitaires.
 */
@RestController
@RequestMapping("/api/convocations")
@RequiredArgsConstructor
@Validated
@Tag(name = "Convocations", description = "Convocations scolaires et universitaires")
public class ConvocationController {

    private final ConvocationService convocationService;
    private final AccessControlService accessControlService;

    @PostMapping
    @Operation(summary = "Créer une convocation", description = "Notifie automatiquement le parent de l'élève")
    public ResponseEntity<ApiResponse<ConvocationResponse>> create(
            @Valid @RequestBody ConvocationRequest request, HttpServletRequest httpRequest) {
        return ok("Convocation créée", convocationService.create(request, httpRequest));
    }

    @GetMapping
    @Operation(summary = "Liste paginée des convocations")
    public ResponseEntity<ApiResponse<com.school.dto.response.PageResponse<ConvocationResponse>>> list(
            @RequestParam(required = false) Long studentId,
            @Min(value = 0, message = "Le numéro de page ne peut pas être négatif") @RequestParam(defaultValue = "0") int page,
            @Max(value = 100, message = "La taille de page ne peut pas dépasser 100")
            @RequestParam(defaultValue = "10") int size) {
        if (studentId != null) {
            accessControlService.assertCanAccessStudentData(studentId);
        }
        return ok("Convocations", com.school.dto.response.PageResponse.from(
                convocationService.listByStudent(studentId, page, size), x -> x));
    }

    @GetMapping("/student/{studentId}")
    @Operation(summary = "Convocations d'un élève/étudiant")
    public ResponseEntity<ApiResponse<List<ConvocationResponse>>> byStudent(@PathVariable Long studentId) {
        accessControlService.assertCanAccessStudentData(studentId);
        return ok("Convocations", convocationService.listByStudentAll(studentId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer une convocation")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        convocationService.delete(id, httpRequest);
        return ok("Convocation supprimée", null);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}
