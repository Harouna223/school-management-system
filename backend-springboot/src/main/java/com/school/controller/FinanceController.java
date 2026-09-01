package com.school.controller;

import com.school.dto.request.ExpenseRequest;
import com.school.dto.request.FeeTypeRequest;
import com.school.dto.response.ApiResponse;
import com.school.dto.response.BookResponse;
import com.school.dto.response.ExpenseResponse;
import com.school.dto.response.FeeTypeResponse;
import com.school.dto.response.PageResponse;
import com.school.service.FinanceService;
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
 * Comptabilité : dépenses, catégories, types de frais, rapports financiers.
 */
@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
@Tag(name = "Comptabilité", description = "Dépenses, types de frais, rapports")
public class FinanceController {

    private final FinanceService financeService;
    private final ReportService reportService;

    @GetMapping("/expenses")
    @Operation(summary = "Rechercher des dépenses")
    public ResponseEntity<ApiResponse<PageResponse<ExpenseResponse>>> expenses(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ok("Dépenses", financeService.searchExpenses(categoryId, from, to, page, size));
    }

    @PostMapping("/expenses")
    public ResponseEntity<ApiResponse<ExpenseResponse>> createExpense(
            @Valid @RequestBody ExpenseRequest request, HttpServletRequest httpRequest) {
        return ok("Dépense enregistrée", financeService.createExpense(request, httpRequest));
    }

    @DeleteMapping("/expenses/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteExpense(@PathVariable Long id,
                                                           HttpServletRequest httpRequest) {
        financeService.deleteExpense(id, httpRequest);
        return ok("Dépense supprimée", null);
    }

    @GetMapping("/fee-types")
    @Operation(summary = "Types de frais scolaires")
    public ResponseEntity<ApiResponse<List<FeeTypeResponse>>> feeTypes() {
        return ok("Types de frais", financeService.listFeeTypes());
    }

    @PostMapping("/fee-types")
    public ResponseEntity<ApiResponse<FeeTypeResponse>> createFeeType(
            @Valid @RequestBody FeeTypeRequest request) {
        return ok("Type de frais créé", financeService.createFeeType(request));
    }

    @PutMapping("/fee-types/{id}")
    public ResponseEntity<ApiResponse<FeeTypeResponse>> updateFeeType(
            @PathVariable Long id, @Valid @RequestBody FeeTypeRequest request) {
        return ok("Type de frais modifié", financeService.updateFeeType(id, request));
    }

    @DeleteMapping("/fee-types/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFeeType(@PathVariable Long id) {
        financeService.deleteFeeType(id);
        return ok("Type de frais supprimé", null);
    }

    @GetMapping("/expense-categories")
    @Operation(summary = "Catégories de dépenses")
    public ResponseEntity<ApiResponse<List<BookResponse.ExpenseCategoryResponse>>> categories() {
        return ok("Catégories", financeService.listExpenseCategories());
    }

    @GetMapping("/summary")
    @Operation(summary = "Synthèse financière (mois en cours + totaux)")
    public ResponseEntity<ApiResponse<FinanceService.FinanceSummary>> summary() {
        return ok("Synthèse financière", financeService.summary());
    }

    @PostMapping("/invoices/mark-overdue")
    @Operation(summary = "Passer en retard (OVERDUE) les factures échues impayées/partielles")
    public ResponseEntity<ApiResponse<Long>> markOverdue() {
        long count = financeService.markOverdue();
        return ok(count + " facture(s) passée(s) en retard", count);
    }

    @PostMapping("/invoices/remind")
    @Operation(summary = "Relancer les parents/élèves pour les factures impayées ou en retard")
    public ResponseEntity<ApiResponse<Long>> remind() {
        long sent = financeService.remindOverdue();
        return ok(sent + " relance(s) envoyée(s)", sent);
    }

    @GetMapping("/report")
    @Operation(summary = "Bilan financier sur une période (encaissé, dépenses, solde)")
    public ResponseEntity<ApiResponse<java.util.Map<String, java.math.BigDecimal>>> report(
            @RequestParam LocalDate from, @RequestParam LocalDate to) {
        java.math.BigDecimal revenue = financeService.revenueBetween(from, to);
        java.math.BigDecimal expenses = financeService.expensesBetween(from, to);
        return ok("Bilan financier", java.util.Map.of(
                "revenue", revenue,
                "expenses", expenses,
                "balance", revenue.subtract(expenses)));
    }

    @GetMapping("/report/export/excel")
    @Operation(summary = "Export Excel du bilan financier")
    public void exportReport(HttpServletResponse response,
                             @RequestParam LocalDate from, @RequestParam LocalDate to) throws IOException {
        java.math.BigDecimal revenue = financeService.revenueBetween(from, to);
        java.math.BigDecimal expenses = financeService.expensesBetween(from, to);
        String[] headers = new String[]{"Poste", "Montant"};
        List<String[]> rows = financeSummaryRows(revenue, expenses);
        reportService.exportExcel(response, headers, rows, "bilan-financier-" + from + "-" + to);
    }

    @GetMapping("/report/export/pdf")
    @Operation(summary = "Export PDF du bilan financier")
    public void exportReportPdf(HttpServletResponse response,
                                @RequestParam LocalDate from, @RequestParam LocalDate to) throws IOException {
        java.math.BigDecimal revenue = financeService.revenueBetween(from, to);
        java.math.BigDecimal expenses = financeService.expensesBetween(from, to);
        String[] headers = new String[]{"Poste", "Montant"};
        List<String[]> rows = financeSummaryRows(revenue, expenses);
        reportService.exportTablePdf(response, "Bilan financier",
                headers, rows,
                "Période", from + " → " + to,
                new String[]{"Solde", String.valueOf(revenue.subtract(expenses))});
    }

    private List<String[]> financeSummaryRows(java.math.BigDecimal revenue,
                                              java.math.BigDecimal expenses) {
        return List.of(
                new String[]{"Encaissé", String.valueOf(revenue)},
                new String[]{"Dépenses", String.valueOf(expenses)},
                new String[]{"Solde", String.valueOf(revenue.subtract(expenses))});
    }

    @GetMapping("/expenses/export/excel")
    @Operation(summary = "Exporter les dépenses en Excel")
    public void exportExpensesExcel(HttpServletResponse response,
                                    @RequestParam(required = false) Long categoryId,
                                    @RequestParam(required = false) LocalDate from,
                                    @RequestParam(required = false) LocalDate to) throws IOException {
        List<ExpenseResponse> expenses = financeService
                .searchExpenses(categoryId, from, to, 0, 10000).getContent();
        String[] headers = new String[]{"N°", "Date", "Catégorie", "Description", "Montant", "Enregistré par"};
        List<String[]> rows = expenseRows(expenses);
        java.math.BigDecimal total = expenses.stream()
                .map(ExpenseResponse::getAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        reportService.exportExcel(response, headers, rows, "depenses");
    }

    @GetMapping("/expenses/export/pdf")
    @Operation(summary = "Exporter les dépenses en PDF (avec total)")
    public void exportExpensesPdf(HttpServletResponse response,
                                  @RequestParam(required = false) Long categoryId,
                                  @RequestParam(required = false) LocalDate from,
                                  @RequestParam(required = false) LocalDate to) throws IOException {
        List<ExpenseResponse> expenses = financeService
                .searchExpenses(categoryId, from, to, 0, 10000).getContent();
        String[] headers = new String[]{"N°", "Date", "Catégorie", "Description", "Montant", "Enregistré par"};
        List<String[]> rows = expenseRows(expenses);
        java.math.BigDecimal total = expenses.stream()
                .map(ExpenseResponse::getAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        reportService.exportTablePdf(response, "Liste des dépenses", headers, rows,
                "Période", (from != null ? from.toString() : "") + " → " + (to != null ? to.toString() : ""),
                new String[]{"TOTAL", String.valueOf(total)});
    }

    private List<String[]> expenseRows(List<ExpenseResponse> expenses) {
        return expenses.stream()
                .map(e -> new String[]{e.getId().toString(),
                        e.getExpenseDate() != null ? e.getExpenseDate().toString() : "",
                        e.getCategoryName() != null ? e.getCategoryName() : "",
                        e.getDescription(), e.getAmount().toString(),
                        e.getPaidByName() != null ? e.getPaidByName() : ""})
                .toList();
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}