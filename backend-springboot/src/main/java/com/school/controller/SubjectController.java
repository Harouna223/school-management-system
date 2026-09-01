package com.school.controller;

import com.school.dto.request.AssignmentRequest;
import com.school.dto.request.SubjectRequest;
import com.school.dto.response.ApiResponse;
import com.school.dto.response.AssignmentResponse;
import com.school.dto.response.PageResponse;
import com.school.dto.response.SubjectResponse;
import com.school.service.SubjectService;
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
 * Module matières : CRUD, coefficients, affectations enseignants.
 */
@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
@Tag(name = "Matières", description = "Gestion des matières et affectations")
public class SubjectController {

    private final SubjectService subjectService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SubjectResponse>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ok("Recherche réussie", subjectService.search(search, page, size));
    }

    @GetMapping("/all")
    @Operation(summary = "Toutes les matières (liste simple)")
    public ResponseEntity<ApiResponse<List<SubjectResponse>>> findAll() {
        return ok("Liste des matières", subjectService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SubjectResponse>> getById(@PathVariable Long id) {
        return ok("Matière trouvée", subjectService.getById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SubjectResponse>> create(@Valid @RequestBody SubjectRequest request,
                                                               HttpServletRequest httpRequest) {
        return ok("Matière créée", subjectService.create(request, httpRequest));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SubjectResponse>> update(@PathVariable Long id,
                                                               @Valid @RequestBody SubjectRequest request,
                                                               HttpServletRequest httpRequest) {
        return ok("Matière modifiée", subjectService.update(id, request, httpRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        subjectService.delete(id, httpRequest);
        return ok("Matière supprimée", null);
    }

    // --- Affectations ---

    @GetMapping("/assignments")
    @Operation(summary = "Liste des affectations", description = "Filtrable par enseignant ou classe")
    public ResponseEntity<ApiResponse<List<AssignmentResponse>>> assignments(
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long classId) {
        return ok("Affectations", subjectService.listAssignments(teacherId, classId));
    }

    @PostMapping("/assignments")
    @Operation(summary = "Affecter un enseignant à une matière dans une classe")
    public ResponseEntity<ApiResponse<AssignmentResponse>> assign(
            @Valid @RequestBody AssignmentRequest request, HttpServletRequest httpRequest) {
        return ok("Affectation créée", subjectService.assign(request, httpRequest));
    }

    @DeleteMapping("/assignments/{id}")
    public ResponseEntity<ApiResponse<Void>> unassign(@PathVariable Long id, HttpServletRequest httpRequest) {
        subjectService.unassign(id, httpRequest);
        return ok("Affectation retirée", null);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}