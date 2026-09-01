package com.school.controller;

import com.school.dto.response.ApiResponse;
import com.school.dto.response.AuditLogResponse;
import com.school.dto.response.DashboardResponse;
import com.school.dto.response.UniversityDashboardResponse;
import com.school.dto.response.PageResponse;
import com.school.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Tableau de bord : statistiques globales et journaux d'audit.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Tableau de bord", description = "Statistiques et KPIs")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    @Operation(summary = "Indicateurs globaux", description = "Effectifs, finances, présences, bibliothèque, filtrable par cycle")
    public ResponseEntity<ApiResponse<DashboardResponse>> stats(
            @RequestParam(required = false) com.school.enums.EducationCycle cycle) {
        return ok("Statistiques chargées", dashboardService.stats(cycle));
    }

    @GetMapping("/university-stats")
    @Operation(summary = "Indicateurs universitaires", description = "Effectifs LMD, filières, programmes, UE, réussite")
    public ResponseEntity<ApiResponse<UniversityDashboardResponse>> universityStats() {
        return ok("Statistiques universitaires", dashboardService.universityStats());
    }

    @GetMapping("/university-report")
    @Operation(summary = "Rapport universitaire", description = "Effectifs par filière/niveau, réussite, dettes académiques")
    public ResponseEntity<ApiResponse<com.school.dto.response.UniversityReportResponse>> universityReport() {
        return ok("Rapport universitaire", dashboardService.universityReport());
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Journal des actions utilisateurs", description = "Filtres : utilisateur, action, entité, période")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> auditLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) java.time.LocalDate from,
            @RequestParam(required = false) java.time.LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok("Journal d'audit", dashboardService.auditLogs(username, action, entity, from, to, page, size));
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}