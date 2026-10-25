package com.school.service;

import com.school.dto.request.PaymentRequest;
import com.school.entity.*;
import com.school.enums.InvoiceStatus;
import com.school.enums.PaymentMethod;
import com.school.exception.BusinessException;
import com.school.repository.*;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests financiers : refus du double encaissement (solde dépassé) et relances de
 * paiement (notification + WhatsApp au parent et à l'élève).
 */
@ExtendWith(MockitoExtension.class)
class FinanceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private FeeTypeRepository feeTypeRepository;
    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private ExpenseCategoryRepository expenseCategoryRepository;
    @Mock
    private StudentService studentService;
    @Mock
    private AuditService auditService;
    @Mock
    private ReportService reportService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private NumberSequenceService numberSequenceService;
    @Mock
    private WhatsAppService whatsAppService;

    private FinanceService service;

    @BeforeEach
    void setUp() {
        service = new FinanceService(invoiceRepository, paymentRepository, feeTypeRepository,
                expenseRepository, expenseCategoryRepository, studentService,
                auditService, notificationService, accessControlService, numberSequenceService,
                whatsAppService);
    }

    private Invoice invoice(BigDecimal amount, BigDecimal discount, BigDecimal paid) {
        return Invoice.builder()
                .id(1L)
                .invoiceNo("FAC-2026-0001")
                .amount(amount)
                .discount(discount)
                .paidAmount(paid)
                .dueDate(LocalDate.now())
                .build();
    }

    @Test
    void paymentAboveRemainingBalanceIsRejected() {
        Invoice invoice = invoice(new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("80"));
        when(invoiceRepository.findWithLockingById(1L)).thenReturn(Optional.of(invoice));

        PaymentRequest request = PaymentRequest.builder()
                .invoiceId(1L)
                .amount(new BigDecimal("30"))
                .method(PaymentMethod.CASH)
                .build();

        assertThatThrownBy(() -> service.recordPayment(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("solde restant");

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void paymentAboveBalanceWithDiscountIsRejected() {
        // Montant 100, remise 20 → solde 80 - 50 payé = 30 restant
        Invoice invoice = invoice(new BigDecimal("100"), new BigDecimal("20"), new BigDecimal("50"));
        when(invoiceRepository.findWithLockingById(1L)).thenReturn(Optional.of(invoice));

        PaymentRequest request = PaymentRequest.builder()
                .invoiceId(1L)
                .amount(new BigDecimal("31"))
                .method(PaymentMethod.CASH)
                .build();

        assertThatThrownBy(() -> service.recordPayment(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("solde restant");
    }

    // ==================================================================
    // Relances de paiement : parent ET élève (notification + WhatsApp)
    // ==================================================================

    /**
     * Une relance de frais impayés doit prévenir le parent <em>et</em> l'élève,
     * par WhatsApp comme par notification interne, dans la même opération.
     */
    @Test
    void relance_previensLeParentEtLEleveParWhatsApp() {
        Parent parent = new Parent();
        parent.setId(4L);
        parent.setFirstName("Koffi");
        parent.setLastName("Ama");
        parent.setPhone("76 12 34 56");
        parent.setUser(new User());

        Student student = new Student();
        student.setId(10L);
        student.setFirstName("Aïcha");
        student.setLastName("Kamdem");
        student.setMatricule("ETU-2026-0010");
        student.setPhone("69 00 00 00");
        student.setParent(parent);
        student.setUser(new User());

        FeeType feeType = new FeeType();
        feeType.setId(1L);
        feeType.setName("Scolarité");

        Invoice unpaid = Invoice.builder()
                .id(1L)
                .invoiceNo("FAC-2026-0001")
                .student(student)
                .feeType(feeType)
                .amount(new BigDecimal("50000"))
                .discount(BigDecimal.ZERO)
                .paidAmount(new BigDecimal("20000"))
                .dueDate(LocalDate.now().minusDays(10))
                .status(InvoiceStatus.UNPAID)
                .build();

        when(invoiceRepository.findOverdue(any())).thenReturn(List.of(unpaid));
        when(invoiceRepository.findByStatusIn(any())).thenReturn(List.of());

        long reminded = service.remindOverdue();

        verify(whatsAppService).sendPaymentReminder(eq(parent), eq(student), any(), any());
        verify(whatsAppService).sendPaymentReminderToStudent(eq(parent), eq(student), any(), any());
        verify(notificationService, times(2)).notify(any(), any(), any(), any(), any());
        assertThat(reminded).isEqualTo(1);
    }

    /**
     * Une facture dont le solde est nul (remise ou paiement complet) ne doit
     * déclencher <em>aucun</em> envoi, même si son statut n'est pas PAID.
     */
    @Test
    void relance_ignoreUneFactureSansSoldeRestant() {
        Student student = new Student();
        student.setId(11L);

        Invoice soldOut = Invoice.builder()
                .id(2L)
                .invoiceNo("FAC-2026-0002")
                .student(student)
                .amount(new BigDecimal("10000"))
                .discount(BigDecimal.ZERO)
                .paidAmount(new BigDecimal("10000"))
                .dueDate(LocalDate.now().minusDays(3))
                .status(InvoiceStatus.PARTIAL)
                .build();

        when(invoiceRepository.findOverdue(any())).thenReturn(List.of(soldOut));
        when(invoiceRepository.findByStatusIn(any())).thenReturn(List.of());

        long reminded = service.remindOverdue();

        assertThat(reminded).isZero();
        verify(whatsAppService, never()).sendPaymentReminder(any(), any(), any(), any());
        verify(whatsAppService, never()).sendPaymentReminderToStudent(any(), any(), any(), any());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }
}
