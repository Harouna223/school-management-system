package com.school.controller;

import com.school.dto.response.ApiResponse;
import com.school.dto.response.AttendanceResponse;
import com.school.dto.response.BulletinResponse;
import com.school.dto.response.InvoiceResponse;
import com.school.dto.response.MyGradeResponse;
import com.school.dto.response.MyTeacherClassResponse;
import com.school.dto.response.ScheduleResponse;
import com.school.dto.response.StudentResponse;
import com.school.dto.response.TeacherResponse;
import com.school.entity.LmdEnrollment;
import com.school.enums.Term;
import com.school.service.MySpaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Espace personnel : chaque compte (élève ou enseignant) n'accède qu'à ses propres données.
 */
@RestController
@RequestMapping("/api/my")
@RequiredArgsConstructor
@Tag(name = "Espace personnel", description = "Données du compte connecté (élève / enseignant)")
public class MySpaceController {

    private final MySpaceService mySpaceService;

    private <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true)
                .message("OK")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @GetMapping("/profile")
    @Operation(summary = "Profil élève du compte connecté")
    public ResponseEntity<ApiResponse<StudentResponse>> myProfile() {
        return ok(mySpaceService.myProfile());
    }

    @GetMapping("/schedule")
    @Operation(summary = "Emploi du temps de la classe de l'élève connecté")
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> mySchedule() {
        return ok(mySpaceService.mySchedule());
    }

    @GetMapping("/grades")
    @Operation(summary = "Notes de l'élève connecté (filtrées par trimestre)")
    public ResponseEntity<ApiResponse<List<MyGradeResponse>>> myGrades(
            @RequestParam(required = false) Term term) {
        return ok(mySpaceService.myGrades(term));
    }

    @GetMapping("/bulletins")
    @Operation(summary = "Bulletins de l'élève connecté")
    public ResponseEntity<ApiResponse<List<BulletinResponse>>> myBulletins() {
        return ok(mySpaceService.myBulletins());
    }

    @GetMapping("/attendances")
    @Operation(summary = "Présences de l'élève connecté")
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> myAttendances() {
        return ok(mySpaceService.myAttendances());
    }

    @GetMapping("/invoices")
    @Operation(summary = "Factures / paiements de l'élève connecté")
    public ResponseEntity<ApiResponse<List<InvoiceResponse>>> myInvoices() {
        return ok(mySpaceService.myInvoices());
    }

    @GetMapping("/teacher-profile")
    @Operation(summary = "Profil enseignant du compte connecté")
    public ResponseEntity<ApiResponse<TeacherResponse>> myTeacherProfile() {
        return ok(mySpaceService.myTeacherProfile());
    }

    @GetMapping("/university")
    @Operation(summary = "Inscriptions LMD de l'étudiant connecté (espace universitaire)")
    public ResponseEntity<ApiResponse<List<LmdEnrollment>>> myUniversity() {
        return ok(mySpaceService.myUniversityEnrollments());
    }

    @GetMapping("/university/releve")
    @Operation(summary = "Relevé universitaire détaillé (UE/notes/crédits) de l'étudiant connecté")
    public ResponseEntity<ApiResponse<com.school.dto.response.LmdReleveResponse>> myUniversityReleve(
            @RequestParam Long fieldId, @RequestParam String semester,
            @RequestParam(defaultValue = "1") int session) {
        return ok(mySpaceService.myUniversityReleve(fieldId, semester, session));
    }

    @GetMapping("/university/history")
    @Operation(summary = "Historique académique universitaire de l'étudiant connecté")
    public ResponseEntity<ApiResponse<List<com.school.entity.EnrollmentHistory>>> myUniversityHistory() {
        return ok(mySpaceService.myUniversityHistory());
    }

    @GetMapping("/teacher/schedule")
    @Operation(summary = "Emploi du temps de l'enseignant connecté")
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> myTeacherSchedule() {
        return ok(mySpaceService.myTeacherSchedule());
    }

    @GetMapping("/teacher/classes")
    @Operation(summary = "Classes et matières de l'enseignant connecté")
    public ResponseEntity<ApiResponse<List<MyTeacherClassResponse>>> myTeacherClasses() {
        return ok(mySpaceService.myTeacherClasses());
    }
}