package com.school.controller;

import com.school.dto.response.AcademicTimelineEntry;
import com.school.dto.response.ApiResponse;
import com.school.dto.response.AttendanceResponse;
import com.school.dto.response.BulletinResponse;
import com.school.dto.response.GradeResponse;
import com.school.dto.response.StudentResponse;
import com.school.entity.LmdEnrollment;
import com.school.service.ParentService;
import com.school.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Espace parent : enfants liés, bulletins, absences et notes (accès contrôlé par lien parent).
 */
@RestController
@RequestMapping("/api/parents")
@RequiredArgsConstructor
@Tag(name = "Espace Parent", description = "Consultation des enfants, bulletins et présences par les parents")
public class ParentController {

    private final ParentService parentService;
    private final ReportService reportService;

    @GetMapping("/children")
    @Operation(summary = "Enfants liés au compte parent")
    public ResponseEntity<ApiResponse<List<StudentResponse>>> children() {
        return ok("Enfants trouvés", parentService.myChildren());
    }

    @GetMapping("/children/{studentId}/bulletins")
    @Operation(summary = "Bulletins de notes d'un enfant")
    public ResponseEntity<ApiResponse<List<BulletinResponse>>> childBulletins(@PathVariable Long studentId) {
        return ok("Bulletins trouvés", parentService.childBulletins(studentId));
    }

    @GetMapping("/children/{studentId}/attendances")
    @Operation(summary = "Présences / absences d'un enfant")
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> childAttendances(@PathVariable Long studentId) {
        return ok("Présences trouvées", parentService.childAttendances(studentId));
    }

    @GetMapping("/children/{studentId}/grades")
    @Operation(summary = "Notes détaillées d'un enfant")
    public ResponseEntity<ApiResponse<List<GradeResponse>>> childGrades(@PathVariable Long studentId) {
        return ok("Notes trouvées", parentService.childGrades(studentId));
    }

    @GetMapping("/children/{studentId}/timeline")
    @Operation(summary = "Parcours académique complet d'un enfant (scolaire + universitaire)")
    public ResponseEntity<ApiResponse<List<AcademicTimelineEntry>>> childTimeline(@PathVariable Long studentId) {
        return ok("Parcours trouvé", parentService.childTimeline(studentId));
    }

    // ---------- Documents universitaires de l'enfant ----------

    @GetMapping("/children/{studentId}/university/enrollments")
    @Operation(summary = "Inscriptions LMD d'un enfant")
    public ResponseEntity<ApiResponse<List<LmdEnrollment>>> childUniversityEnrollments(@PathVariable Long studentId) {
        return ok("Inscriptions", parentService.childUniversityEnrollments(studentId));
    }

    @GetMapping("/children/{studentId}/university/releve")
    @Operation(summary = "Relevé universitaire PDF d'un enfant")
    public void childRelevePdf(@PathVariable Long studentId, @RequestParam Long fieldId,
                               @RequestParam String semester, @RequestParam(defaultValue = "1") int session,
                               HttpServletResponse response) throws java.io.IOException {
        reportService.universityRelevePdf(response,
                parentService.childUniversityReleve(studentId, fieldId, semester, session));
    }

    @GetMapping("/children/{studentId}/university/attestation")
    @Operation(summary = "Attestation de réussite PDF d'un enfant")
    public void childAttestationPdf(@PathVariable Long studentId, @RequestParam Long fieldId,
                                    @RequestParam String semester, @RequestParam(defaultValue = "1") int session,
                                    HttpServletResponse response) throws java.io.IOException {
        reportService.universityAttestationPdf(response,
                parentService.childUniversityAttestation(studentId, fieldId, semester, session));
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}