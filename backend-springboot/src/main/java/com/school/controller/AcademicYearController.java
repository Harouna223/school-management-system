package com.school.controller;

import com.school.dto.request.AcademicYearRequest;
import com.school.dto.response.ApiResponse;
import com.school.entity.AcademicYear;
import com.school.service.AcademicYearService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Gestion des années scolaires.
 */
@RestController
@RequestMapping("/api/academic-years")
@RequiredArgsConstructor
@Tag(name = "Années scolaires", description = "Gestion des années scolaires de l'établissement")
public class AcademicYearController {

    private final AcademicYearService academicYearService;

    @GetMapping
    @Operation(summary = "Liste des années scolaires")
    public ResponseEntity<ApiResponse<List<AcademicYear>>> findAll() {
        return ok("Années scolaires", academicYearService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AcademicYear>> getById(@PathVariable Long id) {
        return ok("Année scolaire trouvée", academicYearService.getById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AcademicYear>> create(@Valid @RequestBody AcademicYearRequest request,
                                                            HttpServletRequest httpRequest) {
        return ok("Année scolaire créée", academicYearService.create(request, httpRequest));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AcademicYear>> update(@PathVariable Long id,
                                                            @Valid @RequestBody AcademicYearRequest request,
                                                            HttpServletRequest httpRequest) {
        return ok("Année scolaire modifiée", academicYearService.update(id, request, httpRequest));
    }

    @PatchMapping("/{id}/current")
    @Operation(summary = "Définir l'année scolaire active")
    public ResponseEntity<ApiResponse<AcademicYear>> setCurrent(@PathVariable Long id,
                                                                HttpServletRequest httpRequest) {
        return ok("Année scolaire active mise à jour", academicYearService.setCurrent(id, httpRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        academicYearService.delete(id, httpRequest);
        return ok("Année scolaire supprimée", null);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}
