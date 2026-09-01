package com.school.controller;

import com.school.dto.request.AttendanceRequest;
import com.school.dto.request.TeacherAttendanceRequest;
import com.school.dto.response.ApiResponse;
import com.school.dto.response.AttendanceReportRow;
import com.school.dto.response.AttendanceResponse;
import com.school.dto.response.TeacherAttendanceResponse;
import com.school.service.AccessControlService;
import com.school.service.AttendanceService;
import com.school.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Module présences : pointage élèves et enseignants, retards, justifications.
 */
@RestController
@RequestMapping("/api/attendances")
@RequiredArgsConstructor
@Tag(name = "Présences", description = "Pointage des présences élèves et enseignants")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final ReportService reportService;
    private final AccessControlService accessControlService;

    @GetMapping("/class/{classId}")
    @Operation(summary = "Présences d'une classe pour une date")
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> byClassAndDate(
            @PathVariable Long classId, @RequestParam LocalDate date) {
        return ok("Présences", attendanceService.listByClassAndDate(classId, date));
    }

    @GetMapping("/student/{studentId}")
    @Operation(summary = "Historique des présences d'un élève")
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> byStudent(@PathVariable Long studentId) {
        accessControlService.assertCanAccessStudentData(studentId);
        return ok("Historique", attendanceService.listByStudent(studentId));
    }

    @PostMapping
    @Operation(summary = "Pointer les présences", description = "Pointage groupé classe/date avec statuts")
    public ResponseEntity<ApiResponse<com.school.dto.response.AttendanceRecordResponse>> record(
            @Valid @RequestBody AttendanceRequest request, HttpServletRequest httpRequest) {
        return ok("Présences enregistrées", attendanceService.record(request, httpRequest));
    }

    @PatchMapping("/{attendanceId}/justify")
    @Operation(summary = "Justifier une absence ou un retard")
    public ResponseEntity<ApiResponse<AttendanceResponse>> justify(
            @PathVariable Long attendanceId, @RequestParam String justification,
            HttpServletRequest httpRequest) {
        return ok("Absence justifiée", attendanceService.justify(attendanceId, justification, httpRequest));
    }

    @GetMapping("/student/{studentId}/stats")
    @Operation(summary = "Statistiques d'absence d'un élève")
    public ResponseEntity<ApiResponse<java.util.Map<String, Long>>> stats(@PathVariable Long studentId) {
        accessControlService.assertCanAccessStudentData(studentId);
        long presences = attendanceService.presentsOf(studentId);
        long absences = attendanceService.absencesOf(studentId);
        long retards = attendanceService.latesOf(studentId);
        return ok("Statistiques", java.util.Map.of(
                "presences", presences,
                "absences", absences,
                "retards", retards,
                "total", presences + absences + retards));
    }

    // ---------- Rapports ----------

    @GetMapping("/report")
    @Operation(summary = "Rapport de présences d'une classe sur une période")
    public ResponseEntity<ApiResponse<List<AttendanceReportRow>>> report(
            @RequestParam Long classId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return ok("Rapport de présences", attendanceService.classReport(classId, from, to));
    }

    @GetMapping("/report/export/excel")
    @Operation(summary = "Export Excel du rapport de présences d'une classe")
    public void exportReport(HttpServletResponse response,
                             @RequestParam Long classId,
                             @RequestParam(required = false) LocalDate from,
                             @RequestParam(required = false) LocalDate to) throws IOException {
        List<AttendanceReportRow> rows = attendanceService.classReport(classId, from, to);
        List<String[]> data = rows.stream()
                .map(r -> new String[]{r.getMatricule(), r.getLastName(), r.getFirstName(),
                        String.valueOf(r.getPresent()), String.valueOf(r.getAbsent()),
                        String.valueOf(r.getLate()), String.valueOf(r.getJustified()),
                        r.getRate() != null ? r.getRate().toString() : "—"})
                .toList();
        reportService.exportExcel(response,
                new String[]{"Matricule", "Nom", "Prénom", "Présents", "Absents", "Retards", "Justifiés", "Taux %"},
                data, "rapport-presences");
    }

    @GetMapping("/report/export/pdf")
    @Operation(summary = "Export PDF du rapport de présences d'une classe")
    public void exportReportPdf(HttpServletResponse response,
                                @RequestParam Long classId,
                                @RequestParam(required = false) LocalDate from,
                                @RequestParam(required = false) LocalDate to) throws IOException {
        List<AttendanceReportRow> rows = attendanceService.classReport(classId, from, to);
        List<String[]> data = rows.stream()
                .map(r -> new String[]{r.getMatricule(), r.getLastName(), r.getFirstName(),
                        String.valueOf(r.getPresent()), String.valueOf(r.getAbsent()),
                        String.valueOf(r.getLate()), String.valueOf(r.getJustified()),
                        r.getRate() != null ? r.getRate().toString() + "%" : "—"})
                .toList();
        reportService.exportTablePdf(response, "Rapport de présences",
                new String[]{"Matricule", "Nom", "Prénom", "Présents", "Absents", "Retards", "Justifiés", "Taux"},
                data, "Classe", "ID: " + classId + " | " + (from != null ? from.toString() : "") + " → " + (to != null ? to.toString() : ""),
                null);
    }

    // ---------- Présences enseignants ----------

    @GetMapping("/teachers")
    @Operation(summary = "Présences des enseignants pour une date")
    public ResponseEntity<ApiResponse<List<TeacherAttendanceResponse>>> teachersByDate(
            @RequestParam LocalDate date) {
        return ok("Présences des enseignants", attendanceService.listTeachersByDate(date));
    }

    @PostMapping("/teachers")
    @Operation(summary = "Pointer les présences des enseignants", description = "Pointage groupé par date")
    public ResponseEntity<ApiResponse<List<TeacherAttendanceResponse>>> recordTeachers(
            @Valid @RequestBody TeacherAttendanceRequest request, HttpServletRequest httpRequest) {
        return ok("Présences des enseignants enregistrées",
                attendanceService.recordTeachers(request, httpRequest));
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}