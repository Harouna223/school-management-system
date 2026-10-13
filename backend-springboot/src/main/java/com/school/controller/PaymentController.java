package com.school.controller;

import com.school.dto.request.InvoiceRequest;
import com.school.dto.request.PaymentRequest;
import com.school.dto.response.ApiResponse;
import com.school.dto.response.InvoiceResponse;
import com.school.dto.response.PageResponse;
import com.school.dto.response.PaymentResponse;
import com.school.enums.InvoiceStatus;
import com.school.enums.PaymentMethod;
import com.school.service.AccessControlService;
import com.school.service.FinanceService;
import com.school.service.ReportService;
import com.school.utils.AmountToWords;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Module finances : factures, paiements, reçus PDF.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Validated
@Tag(name = "Finances", description = "Factures, paiements et reçus")
public class PaymentController {

    private final FinanceService financeService;
    private final ReportService reportService;
    private final AccessControlService accessControlService;

    @GetMapping("/invoices")
    @Operation(summary = "Rechercher des factures")
    public ResponseEntity<ApiResponse<PageResponse<InvoiceResponse>>> searchInvoices(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) InvoiceStatus status,
            @Min(value = 0, message = "Le numéro de page ne peut pas être négatif") @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        // Un PARENT / ELEVE ne peut pas élargir le périmètre : sans identifiant
        // il ne voit que ses propres factures, avec un identifiant étranger il
        // est refusé (au lieu de lister tout l'établissement).
        var scope = accessControlService.resolveStudentScope(studentId);
        return ok("Factures", financeService.searchInvoices(scope, status, page, size));
    }

    @GetMapping("/invoices/student/{studentId}")
    @Operation(summary = "Factures d'un élève")
    public ResponseEntity<ApiResponse<List<InvoiceResponse>>> invoicesByStudent(
            @PathVariable Long studentId) {
        accessControlService.assertCanAccessStudentData(studentId);
        return ok("Factures de l'élève", financeService.invoicesOfStudent(studentId));
    }

    @PostMapping("/invoices")
    @Operation(summary = "Créer une facture")
    public ResponseEntity<ApiResponse<InvoiceResponse>> createInvoice(
            @Valid @RequestBody InvoiceRequest request, HttpServletRequest httpRequest) {
        return ok("Facture créée", financeService.createInvoice(request, httpRequest));
    }

    @GetMapping
    @Operation(summary = "Rechercher des paiements")
    public ResponseEntity<ApiResponse<PageResponse<PaymentResponse>>> searchPayments(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @Min(value = 0, message = "Le numéro de page ne peut pas être négatif") @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        var scope = accessControlService.resolveStudentScope(studentId);
        return ok("Paiements", financeService.searchPayments(scope, method, from, to, page, size));
    }

    @GetMapping("/student/{studentId}")
    @Operation(summary = "Paiements d'un élève")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> paymentsByStudent(
            @PathVariable Long studentId) {
        accessControlService.assertCanAccessStudentData(studentId);
        return ok("Paiements de l'élève", financeService.paymentsOfStudent(studentId));
    }

    @PostMapping
    @Operation(summary = "Enregistrer un paiement", description = "Génère le reçu, met à jour la facture")
    public ResponseEntity<ApiResponse<PaymentResponse>> recordPayment(
            @Valid @RequestBody PaymentRequest request, HttpServletRequest httpRequest) {
        return ok("Paiement enregistré", financeService.recordPayment(request, httpRequest));
    }

    @GetMapping("/{paymentId}/receipt/pdf")
    @Operation(summary = "Télécharger le reçu PDF d'un paiement")
    public void receiptPdf(@PathVariable Long paymentId, HttpServletResponse response) throws IOException {
        PaymentResponse payment;
        try {
            payment = financeService.getPayment(paymentId);
        } catch (com.school.exception.ResourceNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        // IDOR : un parent / élève ne peut télécharger que les reçus des élèves
        // auxquels il a accès, même en devinant l'identifiant du paiement.
        accessControlService.assertCanAccessStudentData(payment.getStudentId());
        java.math.BigDecimal remaining = null;
        if (payment.getInvoiceId() != null) {
            try {
                remaining = financeService.getInvoice(payment.getInvoiceId()).getRemainingAmount();
            } catch (Exception ignored) {
                // solde restant indisponible
            }
        }
        reportService.paymentReceiptPdf(response, payment.getReceiptNo(), payment.getStudentName(),
                payment.getMatricule(), payment.getInvoiceNo(), payment.getAmount().toString(),
                payment.getMethod().name(),
                payment.getPaymentDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                payment.getNote(), AmountToWords.toWords(payment.getAmount()),
                remaining != null ? remaining.toString() : null);
    }

    @GetMapping("/export/pdf")
    @Operation(summary = "Exporter les paiements en PDF (avec total)")
    public void exportPaymentsPdf(HttpServletResponse response,
                                  @RequestParam(required = false) Long studentId,
                                  @RequestParam(required = false) PaymentMethod method,
                                  @RequestParam(required = false) LocalDate from,
                                  @RequestParam(required = false) LocalDate to) throws IOException {
        var scope = accessControlService.resolveStudentScope(studentId);
        List<PaymentResponse> payments = financeService
                .searchPayments(scope, method, from, to, 0, 10000).getContent();
        String[] headers = new String[]{"Reçu", "Élève", "Matricule", "Facture", "Montant", "Mode", "Date", "Enregistré par"};
        List<String[]> rows = paymentRows(payments);
        java.math.BigDecimal total = payments.stream()
                .map(com.school.dto.response.PaymentResponse::getAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        reportService.exportTablePdf(response, "Historique des paiements", headers, rows,
                "Période", (from != null ? from.toString() : "") + " → " + (to != null ? to.toString() : ""),
                new String[]{"TOTAL", String.valueOf(total)});
    }

    @GetMapping("/invoices/export/pdf")
    @Operation(summary = "Exporter les factures en PDF (avec total)")
    public void exportInvoicesPdf(HttpServletResponse response,
                                  @RequestParam(required = false) Long studentId,
                                  @RequestParam(required = false) InvoiceStatus status) throws IOException {
        var scope = accessControlService.resolveStudentScope(studentId);
        List<InvoiceResponse> invoices = financeService
                .searchInvoices(scope, status, 0, 10000)
                .getContent();
        String[] headers = new String[]{"N° Facture", "Élève", "Matricule", "Frais", "Montant", "Payé", "Restant", "Statut", "Échéance"};
        List<String[]> rows = invoiceRows(invoices);
        java.math.BigDecimal total = invoices.stream()
                .map(InvoiceResponse::getAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        reportService.exportTablePdf(response, "Liste des factures", headers, rows, null, null,
                new String[]{"TOTAL", String.valueOf(total)});
    }

    private List<String[]> paymentRows(List<PaymentResponse> payments) {
        return payments.stream()
                .map(p -> new String[]{p.getReceiptNo(), p.getStudentName(), p.getMatricule(),
                        p.getInvoiceNo(), p.getAmount().toString(),
                        p.getMethod() != null ? p.getMethod().name() : "",
                        p.getPaymentDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                        p.getRecordedByName() != null ? p.getRecordedByName() : ""})
                .toList();
    }

    private List<String[]> invoiceRows(List<InvoiceResponse> invoices) {
        return invoices.stream()
                .map(i -> new String[]{i.getInvoiceNo(), i.getStudentName(), i.getMatricule(),
                        i.getFeeTypeName() != null ? i.getFeeTypeName() : "",
                        i.getAmount().toString(), i.getPaidAmount().toString(),
                        i.getRemainingAmount() != null ? i.getRemainingAmount().toString() : "",
                        i.getStatus() != null ? i.getStatus().name() : "",
                        i.getDueDate() != null ? i.getDueDate().toString() : ""})
                .toList();
    }

    @GetMapping("/export/excel")
    @Operation(summary = "Exporter les paiements en Excel")
    public void exportPaymentsExcel(HttpServletResponse response,
                                    @RequestParam(required = false) Long studentId,
                                    @RequestParam(required = false) PaymentMethod method,
                                    @RequestParam(required = false) LocalDate from,
                                    @RequestParam(required = false) LocalDate to) throws IOException {
        var scope = accessControlService.resolveStudentScope(studentId);
        List<PaymentResponse> payments = financeService
                .searchPayments(scope, method, from, to, 0, 10000).getContent();
        reportService.exportExcel(response,
                new String[]{"Reçu", "Élève", "Matricule", "Facture", "Montant", "Mode", "Date", "Enregistré par"},
                paymentRows(payments), "paiements");
    }

    @GetMapping("/invoices/export/excel")
    @Operation(summary = "Exporter les factures en Excel")
    public void exportInvoicesExcel(HttpServletResponse response,
                                    @RequestParam(required = false) Long studentId,
                                    @RequestParam(required = false) InvoiceStatus status) throws IOException {
        var scope = accessControlService.resolveStudentScope(studentId);
        List<InvoiceResponse> invoices = financeService
                .searchInvoices(scope, status, 0, 10000)
                .getContent();
        reportService.exportExcel(response,
                new String[]{"N° Facture", "Élève", "Matricule", "Frais", "Montant", "Payé", "Restant", "Statut", "Échéance"},
                invoiceRows(invoices), "factures");
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}