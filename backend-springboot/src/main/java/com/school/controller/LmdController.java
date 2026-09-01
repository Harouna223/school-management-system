package com.school.controller;

import com.school.dto.request.LmdEnrollmentRequest;
import com.school.dto.request.UeGradeRequest;
import com.school.dto.response.ApiResponse;
import com.school.dto.response.LmdDeliberationResult;
import com.school.entity.*;
import com.school.service.AccessControlService;
import com.school.service.LmdService;
import com.school.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Module universitaire LMD : structure, UE, notes, délibérations.
 */
@RestController
@RequestMapping("/api/lmd")
@RequiredArgsConstructor
@Tag(name = "Université LMD", description = "Module universitaire : structure, UE, EC, notes, délibérations")
public class LmdController {

    private final LmdService lmdService;
    private final ReportService reportService;
    private final AccessControlService accessControlService;

    // ---------- Facultés ----------

    @GetMapping("/faculties")
    public ResponseEntity<ApiResponse<List<Faculty>>> faculties() {
        return ok("Facultés", lmdService.listFaculties());
    }

    @PostMapping("/faculties")
    public ResponseEntity<ApiResponse<Faculty>> createFaculty(@RequestBody Faculty faculty,
                                                              HttpServletRequest httpRequest) {
        return ok("Faculté créée", lmdService.saveFaculty(faculty, httpRequest));
    }

    @DeleteMapping("/faculties/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFaculty(@PathVariable Long id, HttpServletRequest httpRequest) {
        lmdService.deleteFaculty(id, httpRequest);
        return ok("Faculté supprimée", null);
    }

    // ---------- Programmes de formation ----------

    @GetMapping("/programs")
    public ResponseEntity<ApiResponse<List<Program>>> programs(@RequestParam(required = false) Long fieldId) {
        return ok("Programmes", lmdService.listPrograms(fieldId));
    }

    @PostMapping("/programs")
    public ResponseEntity<ApiResponse<Program>> createProgram(@RequestBody Program program,
                                                              HttpServletRequest httpRequest) {
        return ok("Programme créé", lmdService.saveProgram(program, httpRequest));
    }

    @DeleteMapping("/programs/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProgram(@PathVariable Long id, HttpServletRequest httpRequest) {
        lmdService.deleteProgram(id, httpRequest);
        return ok("Programme supprimé", null);
    }

    // ---------- Semestres ----------

    @GetMapping("/semesters")
    public ResponseEntity<ApiResponse<List<Semester>>> semesters(@RequestParam Long fieldId) {
        return ok("Semestres", lmdService.listSemesters(fieldId));
    }

    @PostMapping("/semesters")
    public ResponseEntity<ApiResponse<Semester>> createSemester(@RequestBody Semester semester,
                                                                HttpServletRequest httpRequest) {
        return ok("Semestre créé", lmdService.saveSemester(semester, httpRequest));
    }

    @DeleteMapping("/semesters/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSemester(@PathVariable Long id, HttpServletRequest httpRequest) {
        lmdService.deleteSemester(id, httpRequest);
        return ok("Semestre supprimé", null);
    }

    // ---------- Groupes / promotions ----------

    @GetMapping("/groups")
    public ResponseEntity<ApiResponse<List<UniversityGroup>>> groups(@RequestParam(required = false) Long fieldId) {
        return ok("Groupes", lmdService.listGroups(fieldId));
    }

    @PostMapping("/groups")
    public ResponseEntity<ApiResponse<UniversityGroup>> createGroup(@RequestBody UniversityGroup group,
                                                                    HttpServletRequest httpRequest) {
        return ok("Groupe créé", lmdService.saveGroup(group, httpRequest));
    }

    @DeleteMapping("/groups/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(@PathVariable Long id, HttpServletRequest httpRequest) {
        lmdService.deleteGroup(id, httpRequest);
        return ok("Groupe supprimé", null);
    }

    // ---------- Éléments Constitutifs (EC) ----------

    @GetMapping("/ecs")
    public ResponseEntity<ApiResponse<List<CourseUnit>>> courseUnits(@RequestParam Long ueId) {
        return ok("EC", lmdService.listCourseUnits(ueId));
    }

    @PostMapping("/ecs")
    public ResponseEntity<ApiResponse<CourseUnit>> createCourseUnit(@RequestBody CourseUnit ec,
                                                                    HttpServletRequest httpRequest) {
        return ok("EC créé", lmdService.saveCourseUnit(ec, httpRequest));
    }

    @DeleteMapping("/ecs/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCourseUnit(@PathVariable Long id, HttpServletRequest httpRequest) {
        lmdService.deleteCourseUnit(id, httpRequest);
        return ok("EC supprimé", null);
    }

    // ---------- Inscription aux UE ----------

    @GetMapping("/ue-enrollments")
    public ResponseEntity<ApiResponse<List<UeEnrollment>>> ueEnrollments(@RequestParam Long studentId) {
        return ok("Inscriptions UE", lmdService.ueEnrollmentsByStudent(studentId));
    }

    @PostMapping("/ue-enrollments")
    public ResponseEntity<ApiResponse<UeEnrollment>> enrollUe(@RequestParam Long studentId,
                                                              @RequestParam Long ueId,
                                                              HttpServletRequest httpRequest) {
        return ok("Inscription UE créée", lmdService.enrollUe(studentId, ueId, httpRequest));
    }

    @DeleteMapping("/ue-enrollments/{id}")
    public ResponseEntity<ApiResponse<Void>> unenrollUe(@PathVariable Long id, HttpServletRequest httpRequest) {
        lmdService.unenrollUe(id, httpRequest);
        return ok("Désinscription UE effectuée", null);
    }

    // ---------- Notes EC ----------

    @PostMapping("/ec-grades")
    public ResponseEntity<ApiResponse<EcGrade>> saveEcGrade(@RequestParam Long studentId,
                                                            @RequestParam Long courseUnitId,
                                                            @RequestParam(defaultValue = "1") int session,
                                                            @RequestParam BigDecimal value,
                                                            @RequestParam(required = false) String appreciation,
                                                            HttpServletRequest httpRequest) {
        return ok("Note EC enregistrée",
                lmdService.saveEcGrade(studentId, courseUnitId, session, value, appreciation, httpRequest));
    }

    @GetMapping("/ec-grades")
    public ResponseEntity<ApiResponse<List<EcGrade>>> ecGrades(@RequestParam Long studentId,
                                                               @RequestParam(required = false) Long fieldId) {
        return ok("Notes EC", lmdService.ecGradesByStudent(studentId, fieldId));
    }

    // ---------- Domaines ----------

    @GetMapping("/domains")
    @Operation(summary = "Domaines académiques (Sciences, Droit, Économie…)")
    public ResponseEntity<ApiResponse<List<Domain>>> domains() {
        return ok("Domaines", lmdService.listDomains());
    }

    @PostMapping("/domains")
    @Operation(summary = "Créer un domaine académique")
    public ResponseEntity<ApiResponse<Domain>> createDomain(@RequestBody Domain domain,
                                                            HttpServletRequest httpRequest) {
        return ok("Domaine créé", lmdService.saveDomain(domain, httpRequest));
    }

    @DeleteMapping("/domains/{id}")
    @Operation(summary = "Supprimer un domaine")
    public ResponseEntity<ApiResponse<Void>> deleteDomain(@PathVariable Long id, HttpServletRequest httpRequest) {
        lmdService.deleteDomain(id, httpRequest);
        return ok("Domaine supprimé", null);
    }

    // ---------- Départements ----------

    @GetMapping("/departments")
    public ResponseEntity<ApiResponse<List<Department>>> departments(@RequestParam(required = false) Long facultyId) {
        return ok("Départements", lmdService.listDepartments(facultyId));
    }

    @PostMapping("/departments")
    public ResponseEntity<ApiResponse<Department>> createDepartment(@RequestBody Department department,
                                                                    HttpServletRequest httpRequest) {
        return ok("Département créé", lmdService.saveDepartment(department, httpRequest));
    }

    @DeleteMapping("/departments/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDepartment(@PathVariable Long id, HttpServletRequest httpRequest) {
        lmdService.deleteDepartment(id, httpRequest);
        return ok("Département supprimé", null);
    }

    // ---------- Filières ----------

    @GetMapping("/fields")
    public ResponseEntity<ApiResponse<List<AcademicField>>> fields(@RequestParam(required = false) Long departmentId) {
        return ok("Filières", lmdService.listFields(departmentId));
    }

    @PostMapping("/fields")
    public ResponseEntity<ApiResponse<AcademicField>> createField(@RequestBody AcademicField field,
                                                                  HttpServletRequest httpRequest) {
        return ok("Filière créée", lmdService.saveField(field, httpRequest));
    }

    @DeleteMapping("/fields/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteField(@PathVariable Long id, HttpServletRequest httpRequest) {
        lmdService.deleteField(id, httpRequest);
        return ok("Filière supprimée", null);
    }

    // ---------- Unités d'enseignement ----------

    @GetMapping("/ues")
    public ResponseEntity<ApiResponse<List<UniversityUnit>>> ues(@RequestParam Long fieldId,
                                                                 @RequestParam(required = false) String semester) {
        return ok("UE", lmdService.listUes(fieldId, semester));
    }

    @PostMapping("/ues")
    public ResponseEntity<ApiResponse<UniversityUnit>> createUe(@RequestBody UniversityUnit ue,
                                                                HttpServletRequest httpRequest) {
        return ok("UE créée", lmdService.saveUe(ue, httpRequest));
    }

    @DeleteMapping("/ues/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUe(@PathVariable Long id, HttpServletRequest httpRequest) {
        lmdService.deleteUe(id, httpRequest);
        return ok("UE supprimée", null);
    }

    // ---------- Inscriptions ----------

    @PostMapping("/enrollments")
    public ResponseEntity<ApiResponse<LmdEnrollment>> enroll(@Valid @RequestBody LmdEnrollmentRequest request,
                                                             HttpServletRequest httpRequest) {
        return ok("Élève inscrit", lmdService.enroll(request, httpRequest));
    }

    @GetMapping("/enrollments")
    public ResponseEntity<ApiResponse<List<LmdEnrollment>>> enrollments(
            @RequestParam(required = false) Long fieldId,
            @RequestParam(required = false) Long studentId) {
        if (studentId != null) accessControlService.assertCanAccessStudent(studentId);
        return ok("Inscriptions",
                studentId != null ? lmdService.enrollmentsByStudent(studentId)
                        : lmdService.enrollmentsByField(fieldId));
    }

    // ---------- Statut d'inscription & historique ----------

    @PatchMapping("/enrollments/{id}/status")
    public ResponseEntity<ApiResponse<LmdEnrollment>> updateEnrollmentStatus(
            @PathVariable Long id, @RequestParam com.school.enums.EnrollmentStatus status,
            @RequestParam(required = false) String reason, HttpServletRequest httpRequest) {
        return ok("Statut mis à jour", lmdService.updateEnrollmentStatus(id, status, reason, httpRequest));
    }

    @PostMapping("/enrollments/{id}/change-level")
    public ResponseEntity<ApiResponse<LmdEnrollment>> changeLevel(
            @PathVariable Long id, @RequestParam(required = false) String level,
            @RequestParam(required = false) String semester, HttpServletRequest httpRequest) {
        return ok("Niveau modifié", lmdService.changeLevel(id, level, semester, httpRequest));
    }

    @GetMapping("/enrollment-history")
    public ResponseEntity<ApiResponse<List<EnrollmentHistory>>> enrollmentHistory(
            @RequestParam Long studentId) {
        return ok("Historique", lmdService.enrollmentHistory(studentId));
    }

    // ---------- Évaluations EC ----------

    @GetMapping("/ec-evaluations")
    public ResponseEntity<ApiResponse<List<EcEvaluation>>> ecEvaluations(
            @RequestParam Long ecId, @RequestParam(required = false) Long studentId) {
        return ok("Évaluations EC", lmdService.ecEvaluations(ecId, studentId));
    }

    @PostMapping("/ec-evaluations")
    public ResponseEntity<ApiResponse<EcEvaluation>> createEcEvaluation(
            @RequestBody EcEvaluation evaluation, HttpServletRequest httpRequest) {
        return ok("Évaluation créée", lmdService.saveEcEvaluation(evaluation, httpRequest));
    }

    @DeleteMapping("/ec-evaluations/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEcEvaluation(
            @PathVariable Long id, HttpServletRequest httpRequest) {
        lmdService.deleteEcEvaluation(id, httpRequest);
        return ok("Évaluation supprimée", null);
    }

    // ---------- Règles académiques ----------

    @GetMapping("/academic-rules")
    public ResponseEntity<ApiResponse<List<AcademicRule>>> academicRules(
            @RequestParam(required = false) String cycle) {
        return ok("Règles académiques", lmdService.listAcademicRules(cycle));
    }

    @PostMapping("/academic-rules")
    public ResponseEntity<ApiResponse<AcademicRule>> createAcademicRule(
            @RequestBody AcademicRule rule, HttpServletRequest httpRequest) {
        return ok("Règle créée", lmdService.saveAcademicRule(rule, httpRequest));
    }

    @DeleteMapping("/academic-rules/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAcademicRule(
            @PathVariable Long id, HttpServletRequest httpRequest) {
        lmdService.deleteAcademicRule(id, httpRequest);
        return ok("Règle supprimée", null);
    }

    // ---------- Notes UE ----------

    @PostMapping("/grades")
    public ResponseEntity<ApiResponse<UeGrade>> saveGrade(@Valid @RequestBody UeGradeRequest request,
                                                          HttpServletRequest httpRequest) {
        return ok("Note UE enregistrée", lmdService.saveUeGrade(request, httpRequest));
    }

    // ---------- Calculs et délibérations ----------

    @GetMapping("/result")
    @Operation(summary = "Résultat d'un semestre pour un élève (moyenne, crédits, UE à repasser, mention)")
    public ResponseEntity<ApiResponse<LmdDeliberationResult>> result(
            @RequestParam Long studentId, @RequestParam Long fieldId, @RequestParam String semester,
            @RequestParam(defaultValue = "1") int session) {
        return ok("Résultat calculé", lmdService.computeResult(studentId, fieldId, semester, session));
    }

    @PostMapping("/deliberate")
    @Operation(summary = "Délibérer une filière pour un semestre et une session")
    public ResponseEntity<ApiResponse<List<LmdDeliberationResult>>> deliberate(
            @RequestParam Long fieldId, @RequestParam String semester,
            @RequestParam(defaultValue = "1") int session, HttpServletRequest httpRequest) {
        return ok("Délibération terminée", lmdService.deliberate(fieldId, semester, session, httpRequest));
    }

    @GetMapping("/deliberations")
    public ResponseEntity<ApiResponse<List<LmdDeliberationResult>>> deliberations(
            @RequestParam Long fieldId, @RequestParam String semester) {
        return ok("Délibérations", lmdService.listDeliberations(fieldId, semester));
    }

    @PatchMapping("/lock")
    @Operation(summary = "Verrouiller / déverrouiller les délibérations")
    public ResponseEntity<ApiResponse<Long>> lock(@RequestParam Long fieldId, @RequestParam String semester,
                                                  @RequestParam boolean locked, HttpServletRequest httpRequest) {
        return ok("Délibérations mises à jour", lmdService.lockDeliberations(fieldId, semester, locked, httpRequest));
    }

    // ---------- Documents universitaires ----------

    @GetMapping("/releve/pdf")
    @Operation(summary = "Relevé universitaire PDF d'un étudiant")
    public void relevePdf(@RequestParam Long studentId, @RequestParam Long fieldId,
                          @RequestParam String semester, @RequestParam(defaultValue = "1") int session,
                          jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        accessControlService.assertCanAccessStudent(studentId);
        reportService.universityRelevePdf(response, lmdService.buildReleve(studentId, fieldId, semester, session));
    }

    @GetMapping("/releve/excel")
    @Operation(summary = "Relevé universitaire Excel d'un étudiant")
    public void releveExcel(@RequestParam Long studentId, @RequestParam Long fieldId,
                            @RequestParam String semester, @RequestParam(defaultValue = "1") int session,
                            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        accessControlService.assertCanAccessStudent(studentId);
        reportService.universityReleveExcel(response, lmdService.buildReleve(studentId, fieldId, semester, session));
    }

    @GetMapping("/attestation/pdf")
    @Operation(summary = "Attestation de réussite PDF avec QR code")
    public void attestationPdf(@RequestParam Long studentId, @RequestParam Long fieldId,
                               @RequestParam String semester, @RequestParam(defaultValue = "1") int session,
                               jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        accessControlService.assertCanAccessStudent(studentId);
        reportService.universityAttestationPdf(response, lmdService.buildAttestation(studentId, fieldId, semester, session));
    }

    // ---------- Emploi du temps universitaire ----------

    @GetMapping("/university-schedules")
    public ResponseEntity<ApiResponse<List<UniversitySchedule>>> universitySchedules(
            @RequestParam(required = false) String semester, @RequestParam(required = false) Long fieldId) {
        return ok("Créneaux", lmdService.listUniversitySchedules(semester, fieldId));
    }

    @PostMapping("/university-schedules")
    public ResponseEntity<ApiResponse<UniversitySchedule>> createUniversitySchedule(
            @RequestBody UniversitySchedule schedule, HttpServletRequest httpRequest) {
        return ok("Créneau créé", lmdService.saveUniversitySchedule(schedule, httpRequest));
    }

    @DeleteMapping("/university-schedules/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUniversitySchedule(@PathVariable Long id,
                                                                      HttpServletRequest httpRequest) {
        lmdService.deleteUniversitySchedule(id, httpRequest);
        return ok("Créneau supprimé", null);
    }

    // ---------- Présences universitaires ----------

    @GetMapping("/university-attendances")
    @Operation(summary = "Présences universitaires par EC, date et type de séance")
    public ResponseEntity<ApiResponse<List<UniversityAttendance>>> universityAttendances(
            @RequestParam(required = false) Long ecId,
            @RequestParam(required = false) java.time.LocalDate date,
            @RequestParam(required = false) com.school.entity.UniversitySchedule.SessionType sessionType) {
        return ok("Présences", lmdService.listUniversityAttendances(ecId, date, sessionType));
    }

    @GetMapping("/student-university-attendances")
    @Operation(summary = "Présences universitaires d'un étudiant")
    public ResponseEntity<ApiResponse<List<UniversityAttendance>>> studentUniversityAttendances(
            @RequestParam Long studentId) {
        return ok("Présences de l'étudiant", lmdService.studentUniversityAttendances(studentId));
    }

    @PostMapping("/university-attendances")
    @Operation(summary = "Pointer une présence universitaire")
    public ResponseEntity<ApiResponse<UniversityAttendance>> saveUniversityAttendance(
            @RequestParam Long ecId, @RequestParam Long studentId,
            @RequestParam java.time.LocalDate date,
            @RequestParam com.school.entity.UniversitySchedule.SessionType sessionType,
            @RequestParam com.school.enums.AttendanceStatus status,
            @RequestParam(required = false) String groupName,
            @RequestParam(required = false) String justification,
            HttpServletRequest httpRequest) {
        return ok("Présence enregistrée",
                lmdService.saveUniversityAttendance(ecId, studentId, date, sessionType, status, groupName, justification, httpRequest));
    }

    @DeleteMapping("/university-attendances/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUniversityAttendance(@PathVariable Long id,
                                                                        HttpServletRequest httpRequest) {
        lmdService.deleteUniversityAttendance(id, httpRequest);
        return ok("Présence supprimée", null);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}
