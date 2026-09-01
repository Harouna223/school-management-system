package com.school.controller;

import com.school.dto.request.ExamRequest;
import com.school.dto.request.GradeRequest;
import com.school.dto.response.ApiResponse;
import com.school.dto.response.BulletinResponse;
import com.school.dto.response.ExamResponse;
import com.school.dto.response.GradeResponse;
import com.school.dto.response.PageResponse;
import com.school.enums.ExamStatus;
import com.school.enums.Term;
import com.school.service.ExamService;
import com.school.service.GradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Module notes et examens : planification, saisie, moyennes, classement, bulletins.
 */
@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
@Tag(name = "Notes & Examens", description = "Évaluations, notes, bulletins")
public class ExamController {

    private final ExamService examService;
    private final GradeService gradeService;

    @GetMapping
    @Operation(summary = "Rechercher des évaluations")
    public ResponseEntity<ApiResponse<PageResponse<ExamResponse>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Term term,
            @RequestParam(required = false) ExamStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ok("Recherche réussie",
                examService.search(search, classId, subjectId, term, status, page, size));
    }

    @GetMapping("/class/{classId}")
    @Operation(summary = "Évaluations d'une classe")
    public ResponseEntity<ApiResponse<List<ExamResponse>>> byClass(@PathVariable Long classId) {
        return ok("Évaluations de la classe", examService.listByClass(classId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExamResponse>> getById(@PathVariable Long id) {
        return ok("Évaluation trouvée", examService.getById(id));
    }

    @PostMapping
    @Operation(summary = "Planifier une évaluation")
    public ResponseEntity<ApiResponse<ExamResponse>> create(@Valid @RequestBody ExamRequest request,
                                                            HttpServletRequest httpRequest) {
        return ok("Évaluation planifiée", examService.create(request, httpRequest));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ExamResponse>> update(@PathVariable Long id,
                                                            @Valid @RequestBody ExamRequest request,
                                                            HttpServletRequest httpRequest) {
        return ok("Évaluation modifiée", examService.update(id, request, httpRequest));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Changer le statut (délibération)")
    public ResponseEntity<ApiResponse<ExamResponse>> changeStatus(@PathVariable Long id,
                                                                  @RequestParam ExamStatus status,
                                                                  HttpServletRequest httpRequest) {
        return ok("Statut mis à jour", examService.changeStatus(id, status, httpRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        examService.delete(id, httpRequest);
        return ok("Évaluation supprimée", null);
    }

    // ---------- Notes ----------

    @GetMapping("/{examId}/grades")
    @Operation(summary = "Notes d'une évaluation")
    public ResponseEntity<ApiResponse<List<GradeResponse>>> gradesByExam(@PathVariable Long examId) {
        return ok("Notes de l'évaluation", gradeService.listByExam(examId));
    }

    @PostMapping("/grades")
    @Operation(summary = "Saisir une note")
    public ResponseEntity<ApiResponse<GradeResponse>> saveGrade(@Valid @RequestBody GradeRequest request,
                                                                HttpServletRequest httpRequest) {
        return ok("Note enregistrée", gradeService.save(request, httpRequest));
    }

    @GetMapping("/student/{studentId}/grades")
    @Operation(summary = "Notes d'un élève")
    public ResponseEntity<ApiResponse<List<GradeResponse>>> gradesByStudent(@PathVariable Long studentId) {
        return ok("Notes de l'élève", gradeService.listByStudent(studentId));
    }

    @GetMapping("/student/{studentId}/average")
    @Operation(summary = "Moyenne trimestrielle d'un élève")
    public ResponseEntity<ApiResponse<java.math.BigDecimal>> average(@PathVariable Long studentId,
                                                                     @RequestParam Term term) {
        return ok("Moyenne calculée", gradeService.averageForStudent(studentId, term));
    }

    @GetMapping("/class/{classId}/ranking")
    @Operation(summary = "Classement des élèves d'une classe")
    public ResponseEntity<ApiResponse<List<GradeService.StudentRank>>> ranking(
            @PathVariable Long classId, @RequestParam Term term) {
        return ok("Classement calculé", gradeService.rankStudents(classId, term));
    }

    @PostMapping("/class/{classId}/bulletins")
    @Operation(summary = "Générer les bulletins d'une classe pour un trimestre")
    public ResponseEntity<ApiResponse<List<BulletinResponse>>> generateBulletins(
            @PathVariable Long classId, @RequestParam Term term, HttpServletRequest httpRequest) {
        return ok("Bulletins générés", gradeService.generateBulletins(classId, term, httpRequest));
    }

    @PostMapping("/class/{classId}/deliberation")
    @Operation(summary = "Lancer la délibération d'une classe (décision admis / ajourné / redouble)")
    public ResponseEntity<ApiResponse<List<BulletinResponse>>> deliberate(
            @PathVariable Long classId, @RequestParam Term term, HttpServletRequest httpRequest) {
        return ok("Délibération effectuée", gradeService.runDeliberation(classId, term, httpRequest));
    }

    @GetMapping("/class/{classId}/deliberation/pv")
    @Operation(summary = "Procès-verbal de délibération (PDF)")
    public void deliberationPv(@PathVariable Long classId, @RequestParam Term term,
                               jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        gradeService.exportDeliberationPv(classId, term, response);
    }

    @GetMapping("/student/{studentId}/bulletins")
    @Operation(summary = "Historique académique d'un élève (bulletins)")
    public ResponseEntity<ApiResponse<List<BulletinResponse>>> bulletinsByStudent(
            @PathVariable Long studentId) {
        return ok("Historique de l'élève", gradeService.bulletinsByStudent(studentId));
    }

    @GetMapping("/bulletins/{bulletinId}/pdf")
    @Operation(summary = "Télécharger le bulletin PDF d'un élève")
    public void bulletinPdf(@PathVariable Long bulletinId, HttpServletResponse response) throws IOException {
        gradeService.exportBulletinPdf(bulletinId, response);
    }

    @GetMapping("/class/{classId}/bulletins/pdf")
    @Operation(summary = "Tous les bulletins de la classe dans un seul document PDF")
    public void classBulletinsPdf(@PathVariable Long classId, @RequestParam Term term,
                                  HttpServletResponse response) throws IOException {
        gradeService.exportClassBulletinsPdf(classId, term, response);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}