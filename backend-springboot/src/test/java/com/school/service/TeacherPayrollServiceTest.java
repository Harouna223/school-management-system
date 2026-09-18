package com.school.service;

import com.school.dto.request.TeacherPayRequest;
import com.school.entity.Teacher;
import com.school.entity.TeacherMonthlyPayment;
import com.school.entity.TeacherWorkHour;
import com.school.enums.Gender;
import com.school.enums.PaymentMethod;
import com.school.enums.TeacherPaymentStatus;
import com.school.enums.TeacherStatus;
import com.school.exception.BusinessException;
import com.school.repository.AcademicYearRepository;
import com.school.repository.ExpenseCategoryRepository;
import com.school.repository.ExpenseRepository;
import com.school.repository.TeacherMonthClosureRepository;
import com.school.repository.TeacherMonthlyPaymentRepository;
import com.school.repository.TeacherPaymentTransactionRepository;
import com.school.repository.TeacherRepository;
import com.school.repository.TeacherWorkHourRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests de la paie des enseignants à l'heure : calcul mensuel
 * (heures × tarif conservé à la saisie), paiement partiel / total,
 * refus des paiements excédentaires, sur salaire déjà soldé ou sans heures.
 */
@ExtendWith(MockitoExtension.class)
class TeacherPayrollServiceTest {

    @Mock
    private TeacherMonthlyPaymentRepository paymentRepository;
    @Mock
    private TeacherPaymentTransactionRepository transactionRepository;
    @Mock
    private TeacherWorkHourRepository workHourRepository;
    @Mock
    private TeacherMonthClosureRepository closureRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private ExpenseCategoryRepository expenseCategoryRepository;
    @Mock
    private AcademicYearRepository academicYearRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private NotificationService notificationService;

    private TeacherPayrollService service;

    @BeforeEach
    void setUp() {
        service = new TeacherPayrollService(paymentRepository, transactionRepository,
                workHourRepository, closureRepository, teacherRepository,
                expenseRepository, expenseCategoryRepository, academicYearRepository,
                auditService, notificationService);
    }

    private Teacher teacher() {
        return Teacher.builder()
                .id(1L)
                .employeeNo("ENS-001")
                .firstName("Amadou")
                .lastName("Traoré")
                .gender(Gender.MALE)
                .hireDate(LocalDate.of(2020, 9, 1))
                .contractType(com.school.enums.ContractType.CDI)
                .status(TeacherStatus.ACTIVE)
                .build();
    }

    private TeacherWorkHourRepository.TeacherMonthTotals totals(BigDecimal hours, BigDecimal amount) {
        return new TeacherWorkHourRepository.TeacherMonthTotals() {
            @Override
            public Long getTeacherId() {
                return 1L;
            }

            @Override
            public BigDecimal getTotalHours() {
                return hours;
            }

            @Override
            public BigDecimal getTotalAmount() {
                return amount;
            }
        };
    }

    private TeacherMonthlyPayment payment(BigDecimal totalAmount, BigDecimal rate) {
        return TeacherMonthlyPayment.builder()
                .id(10L)
                .teacher(teacher())
                .monthDate(LocalDate.of(2026, 9, 1))
                .totalHours(new BigDecimal("84"))
                .hourlyRate(rate)
                .totalAmount(totalAmount)
                .amountPaid(BigDecimal.ZERO)
                .remainingAmount(totalAmount)
                .status(TeacherPaymentStatus.PENDING)
                .build();
    }

    /** Simule les heures du mois : chaque saisie garde son propre tarif (historisation). */
    private void monthHours(BigDecimal hours, BigDecimal amount, BigDecimal lastRate) {
        when(workHourRepository.sumByMonth(any(), any(), any()))
                .thenReturn(List.of(totals(hours, amount)));
        TeacherWorkHour last = TeacherWorkHour.builder()
                .id(99L)
                .hours(hours)
                .hourlyRateApplied(lastRate)
                .amount(amount)
                .build();
        when(workHourRepository.findTop1ByTeacherIdAndDateBetweenOrderByIdDesc(
                anyLong(), any(), any())).thenReturn(Optional.of(last));
    }

    private TeacherPayRequest payRequest(BigDecimal amount, LocalDate monthDate) {
        return TeacherPayRequest.builder()
                .teacherId(1L)
                .monthDate(monthDate)
                .amount(amount)
                .method(PaymentMethod.CASH)
                .build();
    }

    /** Bulletin existant déjà présent en base, heures du mois simulées. */
    private void givenMonthlyContext(BigDecimal totalAmount, BigDecimal rate,
                                     BigDecimal hours, BigDecimal alreadyPaid) {
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher()));
        when(paymentRepository.findByTeacherIdAndMonthDate(1L, LocalDate.of(2026, 9, 1)))
                .thenReturn(Optional.of(payment(totalAmount, rate)));
        monthHours(hours, totalAmount, rate);
        lenient().when(transactionRepository.sumAmountOf(10L)).thenReturn(alreadyPaid);
        lenient().when(paymentRepository.save(any(TeacherMonthlyPayment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void paymentAboveRemainingBalanceIsRejected() {
        // Test 7 : dû 200 000 ; tentative de payer 250 000 → refus
        givenMonthlyContext(new BigDecimal("200000"), new BigDecimal("2000"),
                new BigDecimal("100"), BigDecimal.ZERO);

        assertThatThrownBy(() -> service.pay(payRequest(new BigDecimal("250000"),
                LocalDate.of(2026, 9, 1)), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("reste à payer");
    }

    @Test
    void paymentOnFullyPaidSalaryIsRejected() {
        // Le salaire est déjà soldé → tout nouveau paiement est refusé
        givenMonthlyContext(new BigDecimal("210000"), new BigDecimal("2500"),
                new BigDecimal("84"), new BigDecimal("210000"));

        assertThatThrownBy(() -> service.pay(payRequest(new BigDecimal("1000"),
                LocalDate.of(2026, 9, 1)), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà entièrement payé");
    }

    @Test
    void paymentWithoutRecordedHoursIsRejected() {
        // Aucune heure saisie → calcul impossible, paiement refusé
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher()));
        when(paymentRepository.findByTeacherIdAndMonthDate(1L, LocalDate.of(2026, 9, 1)))
                .thenReturn(Optional.of(payment(BigDecimal.ZERO, BigDecimal.ZERO)));
        when(workHourRepository.sumByMonth(any(), any(), any())).thenReturn(List.of());
        lenient().when(workHourRepository.findTop1ByTeacherIdAndDateBetweenOrderByIdDesc(
                anyLong(), any(), any())).thenReturn(Optional.empty());
        lenient().when(transactionRepository.sumAmountOf(10L)).thenReturn(BigDecimal.ZERO);
        lenient().when(paymentRepository.save(any(TeacherMonthlyPayment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.pay(payRequest(new BigDecimal("5000"),
                LocalDate.of(2026, 9, 1)), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Aucune heure enseignée");
    }

    @Test
    void invalidMonthDayIsRejected() {
        // Le mois doit être transmis au premier jour (2026-09-01)
        assertThatThrownBy(() -> service.pay(payRequest(new BigDecimal("1000"),
                LocalDate.of(2026, 9, 13)), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("premier jour du mois");
    }

    @Test
    void nonPositiveAmountIsRejected() {
        assertThatThrownBy(() -> service.pay(payRequest(BigDecimal.ZERO,
                LocalDate.of(2026, 9, 1)), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("strictement positif");
    }

    @Test
    void partialThenTotalPaymentLeadToPaidStatus() {
        // Tests 5 et 6 : 200 000 dus ; 100 000 → PARTIAL ; + 100 000 → PAYÉ
        TeacherMonthlyPayment stored = payment(new BigDecimal("200000"), new BigDecimal("2000"));
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher()));
        when(paymentRepository.findByTeacherIdAndMonthDate(1L, LocalDate.of(2026, 9, 1)))
                .thenReturn(Optional.of(stored));
        monthHours(new BigDecimal("100"), new BigDecimal("200000"), new BigDecimal("2000"));
        when(paymentRepository.save(any(TeacherMonthlyPayment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // Aucun versement antérieur, puis 100 000 après le 1er paiement,
        // 100 000 au contrôle du 2e paiement, 200 000 après le 2e paiement
        when(transactionRepository.sumAmountOf(10L))
                .thenReturn(BigDecimal.ZERO, new BigDecimal("100000"),
                        new BigDecimal("100000"), new BigDecimal("200000"));
        when(transactionRepository.count()).thenReturn(0L, 1L);
        when(transactionRepository.save(any(com.school.entity.TeacherPaymentTransaction.class)))
                .thenAnswer(inv -> {
                    com.school.entity.TeacherPaymentTransaction tx = inv.getArgument(0);
                    tx.setId(77L);
                    return tx;
                });
        when(expenseCategoryRepository.findAll()).thenReturn(List.of());
        when(expenseCategoryRepository.save(any(com.school.entity.ExpenseCategory.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(expenseRepository.save(any(com.school.entity.Expense.class)))
                .thenAnswer(inv -> com.school.entity.Expense.builder().id(555L).build());

        var first = service.pay(payRequest(new BigDecimal("100000"),
                LocalDate.of(2026, 9, 1)), null);

        assertThat(first.getAmountPaidAfter()).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(first.getRemainingAfter()).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(stored.getStatus()).isEqualTo(TeacherPaymentStatus.PARTIAL);
        // Intégration comptable : une dépense créée pour la transaction
        assertThat(first.getExpenseId()).isEqualTo(555L);
        assertThat(first.getTotalHours()).isEqualByComparingTo(new BigDecimal("100"));

        // 2e versement : l'historique réel passe progressivement à 200 000 → salaire soldé
        var second = service.pay(payRequest(new BigDecimal("100000"),
                LocalDate.of(2026, 9, 1)), null);

        assertThat(second.getAmountPaidAfter()).isEqualByComparingTo(new BigDecimal("200000"));
        assertThat(second.getRemainingAfter()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stored.getStatus()).isEqualTo(TeacherPaymentStatus.PAID);
    }

    @Test
    void monthlyRowsUseAmountsStoredAtEntryTime() {
        // Test 8 : le tarif et le montant figés à la saisie restent utilisés
        // même si le tarif horaire du professeur change ensuite (historisation).
        Teacher teacher = teacher();
        when(workHourRepository.sumByMonth(any(), any(), any()))
                .thenReturn(List.of(totals(new BigDecimal("100"), new BigDecimal("200000"))));
        when(paymentRepository.search(any(), any(), any(), any())).thenReturn(List.of());
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher));
        when(closureRepository.existsByMonthDateAndClosedTrue(LocalDate.of(2026, 1, 1)))
                .thenReturn(false);
        when(workHourRepository.findTop1ByTeacherIdAndDateBetweenOrderByIdDesc(
                anyLong(), any(), any())).thenReturn(Optional.of(TeacherWorkHour.builder()
                        .hourlyRateApplied(new BigDecimal("2000"))
                        .amount(new BigDecimal("200000"))
                        .build()));

        var rows = service.monthlyRows(LocalDate.of(2026, 1, 1), null, null);

        assertThat(rows).hasSize(1);
        // 100 h × 2 000 = 200 000 : l'ancien tarif est conservé
        assertThat(rows.get(0).getTotalAmount()).isEqualByComparingTo(new BigDecimal("200000"));
        assertThat(rows.get(0).getHourlyRate()).isEqualByComparingTo(new BigDecimal("2000"));
    }
}
