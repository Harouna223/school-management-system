package com.school.service;

import com.school.dto.request.TeacherPayRequest;
import com.school.dto.response.TeacherPaymentTransactionResponse;
import com.school.dto.response.TeacherPayrollRowResponse;
import com.school.entity.AcademicYear;
import com.school.entity.Expense;
import com.school.entity.ExpenseCategory;
import com.school.entity.Teacher;
import com.school.entity.TeacherMonthlyPayment;
import com.school.entity.TeacherPaymentTransaction;
import com.school.entity.TeacherWorkHour;
import com.school.entity.User;
import com.school.enums.NotificationType;
import com.school.enums.PaymentMethod;
import com.school.enums.TeacherPaymentStatus;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.AcademicYearRepository;
import com.school.repository.ExpenseCategoryRepository;
import com.school.repository.ExpenseRepository;
import com.school.repository.TeacherMonthClosureRepository;
import com.school.repository.TeacherMonthlyPaymentRepository;
import com.school.repository.TeacherPaymentTransactionRepository;
import com.school.repository.TeacherRepository;
import com.school.repository.TeacherWorkHourRepository;
import com.school.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Paie des enseignants à l'heure : calcul mensuel (heures × tarif), paiements
 * complets/partiels, historique des transactions, clôture mensuelle et
 * intégration comptable (dépense « Salaires enseignants »).
 * <p>
 * Tous les montants sont recalculés côté serveur depuis les saisies
 * journalières ; le frontend n'a jamais le dernier mot.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TeacherPayrollService {

    /** Catégorie comptable utilisée pour les salaires des enseignants. */
    public static final String EXPENSE_CATEGORY = "Salaires enseignants";

    private static final String[] MONTHS_FR = {
            "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
            "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"
    };

    private final TeacherMonthlyPaymentRepository paymentRepository;
    private final TeacherPaymentTransactionRepository transactionRepository;
    private final TeacherWorkHourRepository workHourRepository;
    private final TeacherMonthClosureRepository closureRepository;
    private final TeacherRepository teacherRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final AcademicYearRepository academicYearRepository;
    private final AuditService auditService;
    private final com.school.service.NotificationService notificationService;

    // ---------- Calcul mensuel ----------

    /** Lignes du tableau « Paie des enseignants » pour un mois. */
    public List<TeacherPayrollRowResponse> monthlyRows(LocalDate monthDate, Long teacherId,
                                                       TeacherPaymentStatus status) {
        LocalDate from = monthDate.withDayOfMonth(1);
        LocalDate to = YearMonth.from(monthDate).atEndOfMonth();
        boolean closed = closureRepository.existsByMonthDateAndClosedTrue(from);

        Map<Long, TeacherWorkHourRepository.TeacherMonthTotals> totals = new LinkedHashMap<>();
        workHourRepository.sumByMonth(from, to, teacherId)
                .forEach(t -> totals.put(t.getTeacherId(), t));

        Map<Long, TeacherMonthlyPayment> payments = new LinkedHashMap<>();
        paymentRepository.search(teacherId, from, null, null)
                .forEach(p -> payments.put(p.getTeacher().getId(), p));

        List<TeacherPayrollRowResponse> rows = new ArrayList<>();
        List<Long> teacherIds = new ArrayList<>();
        teacherIds.addAll(totals.keySet());
        payments.keySet().stream().filter(id -> !teacherIds.contains(id)).forEach(teacherIds::add);

        for (Long id : teacherIds) {
            Teacher teacher = teacherRepository.findById(id).orElse(null);
            if (teacher == null) {
                continue;
            }
            var monthTotals = totals.get(id);
            BigDecimal totalHours = monthTotals != null ? monthTotals.getTotalHours() : BigDecimal.ZERO;
            BigDecimal totalAmount = monthTotals != null ? monthTotals.getTotalAmount() : BigDecimal.ZERO;

            TeacherMonthlyPayment payment = payments.get(id);
            BigDecimal amountPaid = payment != null ? payment.getAmountPaid() : BigDecimal.ZERO;
            BigDecimal remaining = totalAmount.subtract(amountPaid);
            TeacherPaymentStatus rowStatus = remaining.signum() <= 0 && totalAmount.signum() > 0
                    ? TeacherPaymentStatus.PAID
                    : amountPaid.signum() > 0 ? TeacherPaymentStatus.PARTIAL : TeacherPaymentStatus.PENDING;
            if (status != null && rowStatus != status) {
                continue;
            }

            rows.add(TeacherPayrollRowResponse.builder()
                    .teacherId(id)
                    .teacherName(teacher.getFullName())
                    .monthlyPaymentId(payment != null ? payment.getId() : null)
                    .totalHours(totalHours)
                    .hourlyRate(referenceRate(id, from, to, payment))
                    .totalAmount(totalAmount)
                    .amountPaid(amountPaid)
                    .remainingAmount(remaining.max(BigDecimal.ZERO))
                    .status(rowStatus)
                    .closed(closed)
                    .build());
        }
        return rows;
    }

    /** Tarif de référence : celui de la dernière saisie du mois, sinon celui du bulletin existant. */
    private BigDecimal referenceRate(Long teacherId, LocalDate from, LocalDate to, TeacherMonthlyPayment payment) {
        Optional<TeacherWorkHour> last =
                workHourRepository.findTop1ByTeacherIdAndDateBetweenOrderByIdDesc(teacherId, from, to);
        if (last.isPresent()) {
            return last.get().getHourlyRateApplied();
        }
        return payment != null ? payment.getHourlyRate() : BigDecimal.ZERO;
    }

    /**
     * Récupère le bulletin mensuel d'un professeur, ou le crée à partir des
     * heures enregistrées (recalculées côté serveur).
     */
    @Transactional
    public TeacherMonthlyPayment ensurePayment(Long teacherId, LocalDate monthDate) {
        LocalDate firstDay = monthDate.withDayOfMonth(1);
        TeacherMonthlyPayment payment = paymentRepository
                .findByTeacherIdAndMonthDate(teacherId, firstDay)
                .orElse(null);
        if (payment == null) {
            Teacher teacher = teacherRepository.findById(teacherId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Enseignant", teacherId));
            payment = TeacherMonthlyPayment.builder()
                    .teacher(teacher)
                    .monthDate(firstDay)
                    .academicYear(resolveYearFor(firstDay))
                    .totalHours(BigDecimal.ZERO)
                    .hourlyRate(BigDecimal.ZERO)
                    .totalAmount(BigDecimal.ZERO)
                    .amountPaid(BigDecimal.ZERO)
                    .remainingAmount(BigDecimal.ZERO)
                    .status(TeacherPaymentStatus.PENDING)
                    .build();
            payment = paymentRepository.save(payment);
        }
        return recalculate(payment);
    }

    /**
     * Recalcule le bulletin depuis les saisies du mois : total d'heures,
     * tarif de référence, montant total, payé, restant et statut.
     * Le montant ne provient JAMAIS du frontend.
     */
    @Transactional
    public TeacherMonthlyPayment recalculate(TeacherMonthlyPayment payment) {
        LocalDate from = payment.getMonthDate();
        LocalDate to = YearMonth.from(from).atEndOfMonth();

        BigDecimal totalHours = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (var t : workHourRepository.sumByMonth(from, to, payment.getTeacher().getId())) {
            totalHours = totalHours.add(t.getTotalHours());
            totalAmount = totalAmount.add(t.getTotalAmount());
        }

        BigDecimal rate = referenceRate(payment.getTeacher().getId(), from, to, payment);
        BigDecimal amountPaid = transactionRepository.sumAmountOf(payment.getId());
        BigDecimal remaining = totalAmount.subtract(amountPaid);

        payment.setTotalHours(totalHours);
        payment.setHourlyRate(rate);
        payment.setTotalAmount(totalAmount);
        payment.setAmountPaid(amountPaid);
        payment.setRemainingAmount(remaining.max(BigDecimal.ZERO));
        payment.setStatus(remaining.signum() <= 0 && totalAmount.signum() > 0
                ? TeacherPaymentStatus.PAID
                : amountPaid.signum() > 0 ? TeacherPaymentStatus.PARTIAL : TeacherPaymentStatus.PENDING);
        return paymentRepository.save(payment);
    }

    public String monthLabel(LocalDate monthDate) {
        return MONTHS_FR[monthDate.getMonthValue() - 1] + " " + monthDate.getYear();
    }

    private AcademicYear resolveYearFor(LocalDate date) {
        return academicYearRepository.findAll().stream()
                .filter(y -> y.getStartDate() != null && y.getEndDate() != null
                        && !y.getStartDate().isAfter(date) && !y.getEndDate().isBefore(date))
                .findFirst()
                .orElseGet(() -> academicYearRepository.findByCurrentTrue().orElse(null));
    }

    // ---------- Paiements ----------

    /**
     * Paiement total ou partiel du salaire mensuel d'un enseignant.
     * Contrôles serveur : montant &gt; 0, pas de paiement excédentaire,
     * pas de paiement si déjà entièrement payé, mois valide.
     * Génère le reçu, la dépense comptable et notifie l'enseignant.
     */
    @Transactional
    public TeacherPaymentTransactionResponse pay(TeacherPayRequest request, HttpServletRequest httpRequest) {
        if (request.getMonthDate().getDayOfMonth() != 1) {
            throw new BusinessException("Le mois doit être le premier jour du mois (ex. 2026-09-01)");
        }
        if (request.getAmount().signum() <= 0) {
            throw new BusinessException("Le montant payé doit être strictement positif");
        }

        Teacher teacher = teacherRepository.findById(request.getTeacherId())
                .orElseThrow(() -> ResourceNotFoundException.of("Enseignant", request.getTeacherId()));
        TeacherMonthlyPayment payment = ensurePayment(teacher.getId(), request.getMonthDate());

        BigDecimal remaining = payment.getTotalAmount().subtract(payment.getAmountPaid());
        if (payment.getTotalAmount().signum() <= 0) {
            throw new BusinessException("Aucune heure enseignée n'est enregistrée pour "
                    + teacher.getFullName() + " en " + monthLabel(request.getMonthDate())
                    + " : le salaire ne peut pas être calculé.");
        }
        if (remaining.signum() <= 0) {
            throw new BusinessException("Le salaire de " + teacher.getFullName() + " pour "
                    + monthLabel(request.getMonthDate()) + " est déjà entièrement payé.");
        }
        if (request.getAmount().compareTo(remaining) > 0) {
            throw new BusinessException("Impossible de payer plus que le reste à payer ("
                    + remaining + " FCFA).");
        }

        long sequence = transactionRepository.count() + 1;
        TeacherPaymentTransaction tx = TeacherPaymentTransaction.builder()
                .monthlyPayment(payment)
                .receiptNo(com.school.utils.CodeGenerator.teacherReceiptNo(sequence))
                .amount(request.getAmount())
                .method(request.getMethod())
                .paymentDate(LocalDateTime.now())
                .reference(request.getReference())
                .receivedBy(SecurityUtils.currentUser())
                .observation(request.getObservation())
                .build();
        tx = transactionRepository.save(tx);

        // Met à jour payé / restant / statut depuis l'historique réel des transactions
        payment = recalculate(payment);

        // Intégration comptable : une seule dépense par transaction de paiement
        tx.setExpenseId(createExpense(tx, payment));
        transactionRepository.save(tx);

        // Notification de l'enseignant (si un compte lui est rattaché)
        if (teacher.getUser() != null) {
            notificationService.notify(teacher.getUser(),
                    "Paiement de salaire",
                    "Salaire " + monthLabel(payment.getMonthDate()) + " : paiement de "
                            + request.getAmount() + " FCFA (reçu " + tx.getReceiptNo()
                            + "). Restant : " + payment.getRemainingAmount() + " FCFA.",
                    NotificationType.PAYMENT, "/my-teaching");
        }

        auditService.log("TEACHER_PAY", "TeacherPaymentTransaction", tx.getId(),
                teacher.getFullName() + " : paiement de " + request.getAmount()
                        + " FCFA pour " + monthLabel(payment.getMonthDate())
                        + " (reçu " + tx.getReceiptNo() + ")", httpRequest);
        return TeacherPaymentTransactionResponse.from(tx);
    }

    /** Intègre le paiement à la comptabilité existante (module dépenses). */
    private Long createExpense(TeacherPaymentTransaction tx, TeacherMonthlyPayment payment) {
        ExpenseCategory category = expenseCategoryRepository.findAll().stream()
                .filter(c -> c.getName().equalsIgnoreCase(EXPENSE_CATEGORY))
                .findFirst()
                .orElseGet(() -> expenseCategoryRepository.save(ExpenseCategory.builder()
                        .name(EXPENSE_CATEGORY)
                        .description("Salaires des enseignants (paie à l'heure)")
                        .build()));
        Expense expense = Expense.builder()
                .description("Salaire enseignant — " + monthLabel(payment.getMonthDate())
                        + " — " + payment.getTeacher().getFullName() + " (reçu " + tx.getReceiptNo() + ")")
                .amount(tx.getAmount())
                .category(category)
                .expenseDate(LocalDate.now())
                .paidBy(SecurityUtils.currentUser())
                .build();
        return expenseRepository.save(expense).getId();
    }

    // ---------- Historique des paiements ----------

    public List<TeacherPaymentTransactionResponse> transactions(Long teacherId, LocalDate monthDate,
                                                                PaymentMethod method,
                                                                LocalDateTime from, LocalDateTime to) {
        return transactionRepository.search(teacherId, monthDate, method, from, to).stream()
                .map(TeacherPaymentTransactionResponse::from).toList();
    }

    public List<TeacherPaymentTransactionResponse> transactionsOf(Long monthlyPaymentId) {
        return transactionRepository.findByMonthlyPaymentIdOrderByPaymentDateDesc(monthlyPaymentId).stream()
                .map(TeacherPaymentTransactionResponse::from).toList();
    }

    public TeacherPaymentTransactionResponse getTransaction(Long transactionId) {
        return TeacherPaymentTransactionResponse.from(findTransaction(transactionId));
    }

    public TeacherPaymentTransaction findTransaction(Long transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> ResourceNotFoundException.of("Reçu de paiement", transactionId));
    }

    // ---------- Clôture / réouverture du mois ----------

    public boolean isClosed(LocalDate monthDate) {
        return closureRepository.existsByMonthDateAndClosedTrue(monthDate.withDayOfMonth(1));
    }

    @Transactional
    public void closeMonth(LocalDate monthDate, HttpServletRequest httpRequest) {
        LocalDate firstDay = monthDate.withDayOfMonth(1);
        if (closureRepository.existsByMonthDateAndClosedTrue(firstDay)) {
            throw new BusinessException("Le mois est déjà clôturé.");
        }
        var closure = closureRepository.findByMonthDate(firstDay)
                .orElseGet(() -> com.school.entity.TeacherMonthClosure.builder()
                        .monthDate(firstDay)
                        .academicYear(resolveYearFor(firstDay))
                        .build());
        closure.setClosed(true);
        closure.setClosedBy(SecurityUtils.currentUser());
        closure.setClosedAt(LocalDateTime.now());
        closure.setReopenedBy(null);
        closure.setReopenedAt(null);
        closureRepository.save(closure);
        auditService.log("MONTH_CLOSE", "TeacherMonthClosure", closure.getId(),
                "Clôture du mois " + monthLabel(firstDay), httpRequest);
    }

    @Transactional
    public void reopenMonth(LocalDate monthDate, HttpServletRequest httpRequest) {
        LocalDate firstDay = monthDate.withDayOfMonth(1);
        var closure = closureRepository.findByMonthDate(firstDay)
                .orElseThrow(() -> new BusinessException("Le mois n'est pas clôturé."));
        closure.setClosed(false);
        closure.setReopenedBy(SecurityUtils.currentUser());
        closure.setReopenedAt(LocalDateTime.now());
        closureRepository.save(closure);
        auditService.log("MONTH_REOPEN", "TeacherMonthClosure", closure.getId(),
                "Réouverture du mois " + monthLabel(firstDay), httpRequest);
    }

    // ---------- Espace enseignant ----------

    private Teacher currentTeacher() {
        User user = SecurityUtils.currentUser();
        if (user == null) {
            throw new BusinessException("Utilisateur non authentifié.");
        }
        return teacherRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException("Aucun profil enseignant n'est rattaché à ce compte."));
    }

    public List<TeacherPayrollRowResponse> myRows(LocalDate monthDate) {
        return monthlyRows(monthDate.withDayOfMonth(1), currentTeacher().getId(), null);
    }

    public List<TeacherPaymentTransactionResponse> myTransactions() {
        return transactions(currentTeacher().getId(), null, null, null, null);
    }

    /** Contrôle IDOR : un enseignant ne peut consulter que ses propres reçus. */
    public TeacherPaymentTransactionResponse assertOwnAndLoad(Long transactionId) {
        TeacherPaymentTransaction tx = findTransaction(transactionId);
        Long ownTeacherId = currentTeacher().getId();
        if (!tx.getMonthlyPayment().getTeacher().getId().equals(ownTeacherId)) {
            throw new BusinessException("Ce reçu ne vous appartient pas : accès refusé.");
        }
        return TeacherPaymentTransactionResponse.from(tx);
    }

    public Long currentTeacherId() {
        return currentTeacher().getId();
    }
}
