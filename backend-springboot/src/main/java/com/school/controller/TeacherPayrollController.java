package com.school.controller;

import com.school.dto.request.TeacherHourlyRateRequest;
import com.school.dto.request.TeacherPayRequest;
import com.school.dto.request.TeacherWorkHourRequest;
import com.school.dto.response.ApiResponse;
import com.school.dto.response.PageResponse;
import com.school.dto.response.TeacherHourlyRateResponse;
import com.school.dto.response.TeacherPaymentTransactionResponse;
import com.school.dto.response.TeacherPayrollRowResponse;
import com.school.dto.response.TeacherWorkHourResponse;
import com.school.enums.TeacherPaymentStatus;
import com.school.service.AcademicYearService;
import com.school.service.ReportService;
import com.school.service.TeacherHoursService;
import com.school.service.TeacherPayrollService;
import com.school.utils.AmountToWords;
import com.school.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Module « Gestion des heures enseignées » et « Paie des enseignants » :
 * tarifs horaires, saisie quotidienne, calcul mensuel, paiements
 * (total/partiel), reçus PDF, rapports, clôture mensuelle.
 */
@RestController
@RequestMapping("/api/teacher-hours")
@RequiredArgsConstructor
@Tag(name = "Heures & Paie des enseignants",
        description = "Tarifs horaires, heures enseignées, salaires mensuels, paiements et reçus")
public class TeacherPayrollController {

    private final TeacherHoursService hoursService;
    private final TeacherPayrollService payrollService;
    private final ReportService reportService;
    private final AcademicYearService academicYearService;

    // ---------- Tarifs horaires ----------

    @GetMapping("/rates")
    @Operation(summary = "Liste des tarifs horaires")
    public ResponseEntity<ApiResponse<List<TeacherHourlyRateResponse>>> rates(
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long academicYearId,
            @RequestParam(required = false) Boolean active) {
        return ok("Tarifs horaires", hoursService.listRates(teacherId, academicYearId, active));
    }

    @PostMapping("/rates")
    @Operation(summary = "Définir le tarif horaire d'un enseignant")
    public ResponseEntity<ApiResponse<TeacherHourlyRateResponse>> createRate(
            @Valid @RequestBody TeacherHourlyRateRequest request, HttpServletRequest httpRequest) {
        return ok("Tarif horaire enregistré",
                hoursService.createRate(request, httpRequest));
    }

    @PatchMapping("/rates/{id}/status")
    @Operation(summary = "Activer / désactiver un tarif horaire")
    public ResponseEntity<ApiResponse<TeacherHourlyRateResponse>> toggleRate(
            @PathVariable Long id, @RequestParam boolean active, HttpServletRequest httpRequest) {
        return ok("Statut du tarif mis à jour", hoursService.toggleRate(id, active, httpRequest));
    }

    // ---------- Saisie quotidienne des heures ----------

    @GetMapping("/work-hours")
    @Operation(summary = "Historique des heures enseignées (filtres + pagination)")
    public ResponseEntity<ApiResponse<PageResponse<TeacherWorkHourResponse>>> workHours(
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long academicYearId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok("Historique des heures",
                hoursService.search(teacherId, subjectId, classId, from, to, academicYearId, page, size));
    }

    @GetMapping("/work-hours/day")
    @Operation(summary = "Saisies d'un professeur pour une journée")
    public ResponseEntity<ApiResponse<List<TeacherWorkHourResponse>>> workHoursOfDay(
            @RequestParam Long teacherId, @RequestParam LocalDate date) {
        return ok("Saisies du jour", hoursService.byTeacherAndDate(teacherId, date));
    }

    @PostMapping("/work-hours")
    @Operation(summary = "Enregistrer les heures enseignées pour une journée")
    public ResponseEntity<ApiResponse<TeacherWorkHourResponse>> record(
            @Valid @RequestBody TeacherWorkHourRequest request, HttpServletRequest httpRequest) {
        return ok("Heures enregistrées", hoursService.record(request, httpRequest));
    }

    @PutMapping("/work-hours/{id}")
    @Operation(summary = "Corriger une saisie journalière")
    public ResponseEntity<ApiResponse<TeacherWorkHourResponse>> update(
            @PathVariable Long id, @Valid @RequestBody TeacherWorkHourRequest request,
            HttpServletRequest httpRequest) {
        return ok("Saisie corrigée", hoursService.update(id, request, httpRequest));
    }

    @DeleteMapping("/work-hours/{id}")
    @Operation(summary = "Supprimer une saisie journalière")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id, HttpServletRequest httpRequest) {
        hoursService.delete(id, httpRequest);
        return ok("Saisie supprimée", null);
    }

    // ---------- Calcul mensuel ----------

    @GetMapping("/monthly")
    @Operation(summary = "Tableau de calcul des salaires pour un mois (heures × tarif)")
    public ResponseEntity<ApiResponse<List<TeacherPayrollRowResponse>>> monthly(
            @RequestParam LocalDate month,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) TeacherPaymentStatus status) {
        return ok("Salaires mensuels",
                payrollService.monthlyRows(month.withDayOfMonth(1), teacherId, status));
    }

    // ---------- Paiements ----------

    @PostMapping("/payments")
    @Operation(summary = "Payer le salaire (total ou partiel)")
    public ResponseEntity<ApiResponse<TeacherPaymentTransactionResponse>> pay(
            @Valid @RequestBody TeacherPayRequest request, HttpServletRequest httpRequest) {
        return ok("Paiement enregistré", payrollService.pay(request, httpRequest));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Historique des paiements")
    public ResponseEntity<ApiResponse<List<TeacherPaymentTransactionResponse>>> transactions(
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) LocalDate month,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to) {
        var paymentMethod = method != null && !method.isBlank()
                ? com.school.enums.PaymentMethod.valueOf(method) : null;
        return ok("Historique des paiements",
                payrollService.transactions(teacherId, month != null ? month.withDayOfMonth(1) : null,
                        paymentMethod, from, to));
    }

    // ---------- Reçu PDF ----------

    @GetMapping("/payments/receipt/{transactionId}")
    @Operation(summary = "Reçu de paiement PDF",
            description = "Un enseignant ne peut consulter que ses propres reçus")
    public void receipt(@PathVariable Long transactionId, HttpServletResponse response) throws IOException {
        writeReceipt(response, isTeacherOnly() ? payrollService.assertOwnAndLoad(transactionId)
                : payrollService.getTransaction(transactionId));
    }

    private void writeReceipt(HttpServletResponse response,
                              TeacherPaymentTransactionResponse tx) throws IOException {
        var p = payrollService.findTransaction(tx.getId()).getMonthlyPayment();
        reportService.teacherPaymentReceiptPdf(response,
                tx.getReceiptNo(),
                tx.getTeacherName(),
                payrollService.monthLabel(p.getMonthDate()),
                p.getAcademicYear() != null ? p.getAcademicYear().getLabel() : null,
                strip(p.getTotalHours()),
                strip(p.getHourlyRate()),
                strip(p.getTotalAmount()),
                strip(tx.getAmount()),
                humanize(tx.getMethod().name()),
                tx.getPaymentDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                tx.getReference(),
                tx.getReceivedByName(),
                strip(p.getRemainingAmount()),
                AmountToWords.toWords(tx.getAmount()));
    }

    // ---------- Rapports ----------

    @GetMapping("/report/monthly")
    @Operation(summary = "État mensuel des paiements des enseignants (PDF)")
    public void monthlyReport(HttpServletResponse response,
                              @RequestParam LocalDate month,
                              @RequestParam(required = false) Long teacherId) throws IOException {
        LocalDate firstDay = month.withDayOfMonth(1);
        List<TeacherPayrollRowResponse> rows = payrollService.monthlyRows(firstDay, teacherId, null);
        List<String[]> data = new ArrayList<>();
        java.math.BigDecimal totalHours = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalDue = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalPaid = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalRemaining = java.math.BigDecimal.ZERO;
        for (var row : rows) {
            data.add(new String[]{row.getTeacherName(), strip(row.getTotalHours()),
                    strip(row.getHourlyRate()), strip(row.getTotalAmount()),
                    strip(row.getAmountPaid()), strip(row.getRemainingAmount()),
                    humanize(row.getStatus().name())});
            totalHours = totalHours.add(row.getTotalHours());
            totalDue = totalDue.add(row.getTotalAmount());
            totalPaid = totalPaid.add(row.getAmountPaid());
            totalRemaining = totalRemaining.add(row.getRemainingAmount());
        }
        reportService.exportTablePdf(response, "État mensuel des paiements des enseignants",
                new String[]{"Professeur", "Heures", "Tarif/h", "Total dû", "Payé", "Reste", "Statut"},
                data, "Mois", payrollService.monthLabel(firstDay)
                        + " | Année scolaire " + currentYearLabel(),
                new String[]{"TOTAUX", totalHours + " h | " + totalDue + " FCFA | "
                        + totalPaid + " FCFA | " + totalRemaining + " FCFA"});
    }

    @GetMapping("/report/monthly/excel")
    @Operation(summary = "État mensuel des paiements des enseignants (Excel)")
    public void monthlyReportExcel(HttpServletResponse response,
                                   @RequestParam LocalDate month,
                                   @RequestParam(required = false) Long teacherId) throws IOException {
        LocalDate firstDay = month.withDayOfMonth(1);
        List<TeacherPayrollRowResponse> rows = payrollService.monthlyRows(firstDay, teacherId, null);
        List<String[]> data = rows.stream()
                .map(row -> new String[]{row.getTeacherName(), strip(row.getTotalHours()),
                        strip(row.getHourlyRate()), strip(row.getTotalAmount()),
                        strip(row.getAmountPaid()), strip(row.getRemainingAmount()),
                        humanize(row.getStatus().name())})
                .toList();
        reportService.exportExcel(response,
                new String[]{"Professeur", "Heures", "Tarif/h", "Total dû", "Payé", "Reste", "Statut"},
                data, "paie-enseignants-" + firstDay.format(DateTimeFormatter.ofPattern("yyyy-MM")));
    }

    // ---------- Clôture mensuelle ----------

    @PostMapping("/months/{month}/close")
    @Operation(summary = "Clôturer le mois (verrouille les heures)", description = "Réservé à la direction")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTEUR')")
    public ResponseEntity<ApiResponse<Void>> closeMonth(
            @PathVariable LocalDate month, HttpServletRequest httpRequest) {
        payrollService.closeMonth(month.withDayOfMonth(1), httpRequest);
        return ok("Mois clôturé", null);
    }

    @PostMapping("/months/{month}/reopen")
    @Operation(summary = "Réouvrir le mois clôturé", description = "Réservé à la direction")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTEUR')")
    public ResponseEntity<ApiResponse<Void>> reopenMonth(
            @PathVariable LocalDate month, HttpServletRequest httpRequest) {
        payrollService.reopenMonth(month.withDayOfMonth(1), httpRequest);
        return ok("Mois réouvert", null);
    }

    @GetMapping("/months/{month}/status")
    @Operation(summary = "Statut de clôture du mois")
    public ResponseEntity<ApiResponse<Boolean>> monthStatus(@PathVariable LocalDate month) {
        return ok("Statut du mois", payrollService.isClosed(month.withDayOfMonth(1)));
    }

    // ---------- Espace enseignant (rôle ENSEIGNANT) ----------

    @GetMapping("/my/monthly")
    @Operation(summary = "Mes heures et mon salaire calculé")
    public ResponseEntity<ApiResponse<List<TeacherPayrollRowResponse>>> myMonthly(
            @RequestParam(required = false) LocalDate month) {
        LocalDate target = month != null ? month.withDayOfMonth(1) : LocalDate.now().withDayOfMonth(1);
        return ok("Mes heures & salaire", payrollService.myRows(target));
    }

    @GetMapping("/my/transactions")
    @Operation(summary = "Mes paiements")
    public ResponseEntity<ApiResponse<List<TeacherPaymentTransactionResponse>>> myTransactions() {
        return ok("Mes paiements", payrollService.myTransactions());
    }

    @GetMapping("/my/receipt/{transactionId}")
    @Operation(summary = "Mon reçu de paiement PDF")
    public void myReceipt(@PathVariable Long transactionId, HttpServletResponse response) throws IOException {
        writeReceipt(response, payrollService.assertOwnAndLoad(transactionId));
    }

    // ---------- Helpers ----------

    private boolean isTeacherOnly() {
        return SecurityUtils.hasRole("ENSEIGNANT")
                && !SecurityUtils.hasRole("SUPER_ADMIN")
                && !SecurityUtils.hasRole("DIRECTEUR")
                && !SecurityUtils.hasRole("COMPTABLE")
                && !SecurityUtils.hasRole("SECRETAIRE");
    }

    private String strip(java.math.BigDecimal value) {
        return value != null ? value.stripTrailingZeros().toPlainString() : "0";
    }

    private String humanize(String enumValue) {
        if (enumValue == null) return "—";
        String label = enumValue.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }

    private String currentYearLabel() {
        try {
            return academicYearService.findAll().stream()
                    .filter(com.school.entity.AcademicYear::isCurrent)
                    .map(com.school.entity.AcademicYear::getLabel)
                    .findFirst().orElse("—");
        } catch (Exception e) {
            return "—";
        }
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}
