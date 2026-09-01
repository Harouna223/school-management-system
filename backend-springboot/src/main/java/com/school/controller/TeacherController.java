package com.school.controller;

import com.school.dto.request.TeacherRequest;
import com.school.dto.response.ApiResponse;
import com.school.dto.response.PageResponse;
import com.school.dto.response.TeacherResponse;
import com.school.enums.TeacherStatus;
import com.school.service.ReportService;
import com.school.service.TeacherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Module enseignants : profils, contrats, salaires, statuts.
 */
@RestController
@RequestMapping("/api/teachers")
@RequiredArgsConstructor
@Tag(name = "Enseignants", description = "Gestion des enseignants")
public class TeacherController {

    private final TeacherService teacherService;
    private final ReportService reportService;

    @GetMapping
    @Operation(summary = "Rechercher des enseignants")
    public ResponseEntity<ApiResponse<PageResponse<TeacherResponse>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TeacherStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ok("Recherche réussie", teacherService.search(search, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TeacherResponse>> getById(@PathVariable Long id) {
        return ok("Enseignant trouvé", teacherService.getById(id));
    }

    @PostMapping
    @Operation(summary = "Embaucher un enseignant", description = "Génère le matricule automatiquement")
    public ResponseEntity<ApiResponse<TeacherResponse>> create(@Valid @RequestBody TeacherRequest request,
                                                               HttpServletRequest httpRequest) {
        return ok("Enseignant embauché avec succès", teacherService.create(request, httpRequest));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TeacherResponse>> update(@PathVariable Long id,
                                                               @Valid @RequestBody TeacherRequest request,
                                                               HttpServletRequest httpRequest) {
        return ok("Enseignant modifié", teacherService.update(id, request, httpRequest));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Changer le statut d'un enseignant")
    public ResponseEntity<ApiResponse<TeacherResponse>> updateStatus(@PathVariable Long id,
                                                                     @RequestParam TeacherStatus status,
                                                                     HttpServletRequest httpRequest) {
        return ok("Statut mis à jour", teacherService.updateStatus(id, status, httpRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        teacherService.delete(id, httpRequest);
        return ok("Enseignant supprimé", null);
    }

    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<TeacherResponse>> uploadPhoto(@PathVariable Long id,
                                                                    @RequestPart("file") MultipartFile file) {
        return ok("Photo enregistrée", teacherService.uploadPhoto(id, file));
    }

    @GetMapping("/export/excel")
    @Operation(summary = "Exporter les enseignants en Excel")
    public void exportExcel(HttpServletResponse response,
                            @RequestParam(required = false) String search,
                            @RequestParam(required = false) TeacherStatus status) throws IOException {
        List<TeacherResponse> teachers = teacherService.search(search, status, 0, 10000).getContent();
        List<String[]> rows = teachers.stream()
                .map(t -> new String[]{t.getEmployeeNo(), t.getLastName(), t.getFirstName(),
                        t.getGender() != null ? t.getGender().name() : "",
                        t.getContractType() != null ? t.getContractType().name() : "",
                        t.getPhone() != null ? t.getPhone() : "",
                        t.getEmail() != null ? t.getEmail() : "",
                        t.getSalary() != null ? t.getSalary().toString() : "",
                        t.getStatus() != null ? t.getStatus().name() : ""})
                .toList();
        reportService.exportExcel(response,
                new String[]{"Matricule", "Nom", "Prénom", "Genre", "Contrat", "Téléphone",
                        "Email", "Salaire", "Statut"},
                rows, "liste-enseignants");
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}