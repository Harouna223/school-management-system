package com.school.service;

import com.school.dto.request.PaymentRequest;
import com.school.entity.*;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests financiers : refus du double encaissement (solde dépassé).
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

    private FinanceService service;

    @BeforeEach
    void setUp() {
        service = new FinanceService(invoiceRepository, paymentRepository, feeTypeRepository,
                expenseRepository, expenseCategoryRepository, studentService,
                auditService, notificationService);
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
}
