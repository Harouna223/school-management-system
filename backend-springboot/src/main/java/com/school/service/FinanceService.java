package com.school.service;

import com.school.dto.request.ExpenseRequest;
import com.school.dto.request.FeeTypeRequest;
import com.school.dto.request.InvoiceRequest;
import com.school.dto.request.PaymentRequest;
import com.school.dto.response.*;
import com.school.entity.*;
import com.school.enums.InvoiceStatus;
import com.school.enums.NotificationType;
import com.school.enums.PaymentMethod;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.*;
import com.school.utils.CodeGenerator;
import com.school.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Module finances : frais, factures, paiements, reçus, dépenses, comptabilité.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FinanceService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final FeeTypeRepository feeTypeRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final StudentService studentService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    // ---------- Factures ----------

    public PageResponse<InvoiceResponse> searchInvoices(Long studentId, InvoiceStatus status,
                                                        int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Invoice> result = invoiceRepository.search(studentId, status, pageable);
        return PageResponse.from(result, InvoiceResponse::from);
    }

    public List<InvoiceResponse> invoicesOfStudent(Long studentId) {
        return invoiceRepository.findByStudentId(studentId).stream()
                .map(InvoiceResponse::from).toList();
    }

    @Transactional
    public InvoiceResponse createInvoice(InvoiceRequest request, HttpServletRequest httpRequest) {
        FeeType feeType = feeTypeRepository.findById(request.getFeeTypeId())
                .orElseThrow(() -> ResourceNotFoundException.of("Type de frais", request.getFeeTypeId()));
        Student student = studentService.findById(request.getStudentId());

        BigDecimal amount = request.getAmount() != null ? request.getAmount() : feeType.getAmount();
        BigDecimal discount = request.getDiscount() != null ? request.getDiscount() : BigDecimal.ZERO;
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("La remise ne peut pas être négative");
        }
        if (discount.compareTo(amount) > 0) {
            throw new BusinessException("La remise ne peut pas dépasser le montant de la facture");
        }

        Invoice invoice = Invoice.builder()
                .invoiceNo(nextInvoiceNo())
                .student(student)
                .feeType(feeType)
                .amount(amount)
                .discount(discount)
                .paidAmount(BigDecimal.ZERO)
                .dueDate(request.getDueDate())
                .status(InvoiceStatus.UNPAID)
                .build();

        Invoice saved = invoiceRepository.save(invoice);
        auditService.log("CREATE", "Invoice", saved.getId(),
                "Facture " + saved.getInvoiceNo() + " pour " + student.getFullName(), httpRequest);
        return InvoiceResponse.from(saved);
    }

    private String nextInvoiceNo() {
        long count = invoiceRepository.count() + 1;
        String no = CodeGenerator.invoiceNo(count);
        while (invoiceRepository.existsByInvoiceNo(no)) {
            no = CodeGenerator.invoiceNo(++count);
        }
        return no;
    }

    // ---------- Paiements ----------

    public PageResponse<PaymentResponse> searchPayments(Long studentId, PaymentMethod method,
                                                        LocalDate from, LocalDate to,
                                                        int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("paymentDate").descending());
        LocalDateTime fromDt = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDt = to != null ? to.atTime(LocalTime.MAX) : null;
        Page<Payment> result = paymentRepository.search(studentId, method, fromDt, toDt, pageable);
        return PageResponse.from(result, PaymentResponse::from);
    }

    public List<PaymentResponse> paymentsOfStudent(Long studentId) {
        return paymentRepository.findByStudentId(studentId).stream()
                .map(PaymentResponse::from).toList();
    }

    public PaymentResponse getPayment(Long id) {
        return PaymentResponse.from(paymentRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Paiement", id)));
    }

    public InvoiceResponse getInvoice(Long id) {
        return InvoiceResponse.from(invoiceRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Facture", id)));
    }

    @Transactional
    public PaymentResponse recordPayment(PaymentRequest request, HttpServletRequest httpRequest) {
        Invoice invoice = invoiceRepository.findWithLockingById(request.getInvoiceId())
                .orElseThrow(() -> ResourceNotFoundException.of("Facture", request.getInvoiceId()));
        Student student = invoice.getStudent();

        BigDecimal discount = invoice.getDiscount() != null ? invoice.getDiscount() : BigDecimal.ZERO;
        BigDecimal remaining = invoice.getAmount().subtract(discount).subtract(invoice.getPaidAmount());
        if (request.getAmount().compareTo(remaining) > 0) {
            throw new BusinessException("Le montant dépasse le solde restant de la facture (" + remaining + ")");
        }

        Payment payment = Payment.builder()
                .receiptNo(nextReceiptNo())
                .invoice(invoice)
                .student(student)
                .amount(request.getAmount())
                .method(request.getMethod())
                .paymentDate(LocalDateTime.now())
                .recordedBy(SecurityUtils.currentUser())
                .note(request.getNote())
                .build();
        Payment saved = paymentRepository.save(payment);

        // Mise à jour de la facture
        invoice.setPaidAmount(invoice.getPaidAmount().add(request.getAmount()));
        if (invoice.getPaidAmount().compareTo(invoice.getAmount().subtract(discount)) >= 0) {
            invoice.setStatus(InvoiceStatus.PAID);
        } else {
            invoice.setStatus(InvoiceStatus.PARTIAL);
        }
        invoiceRepository.save(invoice);

        auditService.log("PAYMENT", "Payment", saved.getId(),
                "Paiement " + saved.getAmount() + " (" + saved.getMethod() + ") - reçu " + saved.getReceiptNo(),
                httpRequest);
        return PaymentResponse.from(saved);
    }

    private String nextReceiptNo() {
        long count = paymentRepository.count() + 1;
        String no = CodeGenerator.receiptNo(count);
        while (paymentRepository.existsByReceiptNo(no)) {
            no = CodeGenerator.receiptNo(++count);
        }
        return no;
    }

    // ---------- Dépenses ----------

    public PageResponse<ExpenseResponse> searchExpenses(Long categoryId, LocalDate from, LocalDate to,
                                                        int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("expenseDate").descending());
        Page<Expense> result = expenseRepository.search(categoryId, from, to, pageable);
        return PageResponse.from(result, ExpenseResponse::from);
    }

    @Transactional
    public ExpenseResponse createExpense(ExpenseRequest request, HttpServletRequest httpRequest) {
        ExpenseCategory category = expenseCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> ResourceNotFoundException.of("Catégorie de dépense",
                        request.getCategoryId()));
        Expense expense = Expense.builder()
                .description(request.getDescription())
                .amount(request.getAmount())
                .category(category)
                .expenseDate(request.getExpenseDate())
                .paidBy(SecurityUtils.currentUser())
                .build();
        Expense saved = expenseRepository.save(expense);
        auditService.log("EXPENSE", "Expense", saved.getId(),
                "Dépense " + saved.getAmount() + " - " + saved.getDescription(), httpRequest);
        return ExpenseResponse.from(saved);
    }

    @Transactional
    public void deleteExpense(Long id, HttpServletRequest httpRequest) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Dépense", id));
        auditService.log("DELETE", "Expense", id, "Suppression dépense " + expense.getDescription(), httpRequest);
        expenseRepository.delete(expense);
    }

    // ---------- Référentiels ----------

    public List<FeeTypeResponse> listFeeTypes() {
        return feeTypeRepository.findAll(Sort.by("name")).stream()
                .map(FeeTypeResponse::from).toList();
    }

    @Transactional
    public FeeTypeResponse createFeeType(FeeTypeRequest request) {
        if (feeTypeRepository.findByName(request.getName()).isPresent()) {
            throw new BusinessException("Un type de frais porte déjà ce nom : " + request.getName());
        }
        FeeType feeType = FeeType.builder()
                .name(request.getName())
                .amount(request.getAmount())
                .description(request.getDescription())
                .build();
        return FeeTypeResponse.from(feeTypeRepository.save(feeType));
    }

    @Transactional
    public FeeTypeResponse updateFeeType(Long id, FeeTypeRequest request) {
        FeeType feeType = feeTypeRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Type de frais", id));
        if (!feeType.getName().equals(request.getName()) && feeTypeRepository.findByName(request.getName()).isPresent()) {
            throw new BusinessException("Un type de frais porte déjà ce nom : " + request.getName());
        }
        feeType.setName(request.getName());
        feeType.setAmount(request.getAmount());
        feeType.setDescription(request.getDescription());
        return FeeTypeResponse.from(feeTypeRepository.save(feeType));
    }

    @Transactional
    public void deleteFeeType(Long id) {
        FeeType feeType = feeTypeRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Type de frais", id));
        if (invoiceRepository.existsByFeeTypeId(id)) {
            throw new BusinessException("Impossible de supprimer : des factures utilisent ce type de frais");
        }
        feeTypeRepository.delete(feeType);
    }

    public List<BookResponse.ExpenseCategoryResponse> listExpenseCategories() {
        return expenseCategoryRepository.findAll(Sort.by("name")).stream()
                .map(BookResponse.ExpenseCategoryResponse::from).toList();
    }

    // ---------- Rapports financiers ----------

    public BigDecimal revenueBetween(LocalDate from, LocalDate to) {
        return paymentRepository.sumBetween(from.atStartOfDay(), to.atTime(LocalTime.MAX));
    }

    public BigDecimal expensesBetween(LocalDate from, LocalDate to) {
        return expenseRepository.sumBetween(from, to.plusDays(1));
    }

    public BigDecimal totalRevenue() {
        return paymentRepository.sumTotal();
    }

    public BigDecimal totalExpenses() {
        return expenseRepository.sumTotal();
    }

    public FinanceSummary summary() {
        LocalDate today = LocalDate.now();
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        return FinanceSummary.builder()
                .monthlyRevenue(revenueBetween(firstOfMonth, today))
                .monthlyExpenses(expensesBetween(firstOfMonth, today))
                .totalRevenue(totalRevenue())
                .totalExpenses(totalExpenses())
                .unpaidInvoices(invoiceRepository.countByStatus(InvoiceStatus.UNPAID)
                        + invoiceRepository.countByStatus(InvoiceStatus.PARTIAL))
                .build();
    }

    @lombok.Builder
    public record FinanceSummary(BigDecimal monthlyRevenue, BigDecimal monthlyExpenses,
                                 BigDecimal totalRevenue, BigDecimal totalExpenses,
                                 long unpaidInvoices) {
    }

    // ---------- Passages en retard (OVERDUE) ----------

    /**
     * Passe en OVERDUE les factures impayées/partielles dont l'échéance est dépassée.
     * Retourne le nombre de factures passées en retard.
     */
    @Transactional
    public long markOverdue() {
        List<Invoice> overdue = invoiceRepository.findOverdue(LocalDate.now());
        for (Invoice invoice : overdue) {
            invoice.setStatus(InvoiceStatus.OVERDUE);
        }
        invoiceRepository.saveAll(overdue);
        return overdue.size();
    }

    /** Exécution quotidienne à 1 h 30 du matin. */
    @Scheduled(cron = "0 30 1 * * *")
    @Transactional
    public void scheduledMarkOverdue() {
        long count = markOverdue();
        if (count > 0) {
            org.slf4j.LoggerFactory.getLogger(getClass())
                    .info("{} facture(s) passée(s) en OVERDUE", count);
        }
    }

    // ---------- Relances de paiement ----------

    /**
     * Relance les parents / élèves pour les factures impayées ou en retard :
     * passe les factures échues en OVERDUE puis crée une notification de relance.
     * Retourne le nombre de relances envoyées.
     */
    @Transactional
    public long remindOverdue() {
        // Récupérer les factures échues AVANT de les basculer en OVERDUE
        List<Invoice> toRemind = new java.util.ArrayList<>(invoiceRepository.findOverdue(LocalDate.now()));
        toRemind.addAll(invoiceRepository.findByStatusIn(List.of(
                com.school.enums.InvoiceStatus.UNPAID,
                com.school.enums.InvoiceStatus.PARTIAL,
                com.school.enums.InvoiceStatus.OVERDUE)));
        java.util.LinkedHashSet<Invoice> unique = new java.util.LinkedHashSet<>(toRemind);
        markOverdue();

        long sent = 0;
        for (Invoice invoice : unique) {
            Student student = invoice.getStudent();
            BigDecimal discount = invoice.getDiscount() != null ? invoice.getDiscount() : BigDecimal.ZERO;
            BigDecimal remaining = invoice.getAmount().subtract(discount).subtract(invoice.getPaidAmount());

            if (student.getParent() != null && student.getParent().getUser() != null) {
                notificationService.notify(student.getParent().getUser(),
                        "Relance de paiement",
                        "Facture " + invoice.getInvoiceNo() + " : solde restant "
                                + remaining + " (" + invoice.getFeeType().getName() + ").",
                        NotificationType.PAYMENT, "/payments");
                sent++;
            }
            if (student.getUser() != null) {
                notificationService.notify(student.getUser(),
                        "Relance de paiement",
                        "Facture " + invoice.getInvoiceNo() + " : solde restant "
                                + remaining + " (" + invoice.getFeeType().getName() + ").",
                        NotificationType.PAYMENT, "/payments");
                sent++;
            }
        }
        if (sent > 0) {
            auditService.log("REMIND", "Invoice", null,
                    "Relances de paiement envoyées : " + sent + " notification(s)", null);
        }
        return sent;
    }
}