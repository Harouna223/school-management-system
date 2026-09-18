package com.school.dto.response;

import com.school.enums.PaymentMethod;
import com.school.entity.TeacherPaymentTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherPaymentTransactionResponse {

    private Long id;
    private Long monthlyPaymentId;
    private Long teacherId;
    private String teacherName;
    private LocalDate monthDate;
    private String receiptNo;
    private BigDecimal amount;
    private PaymentMethod method;
    private LocalDateTime paymentDate;
    private String reference;
    private String receivedByName;
    private String observation;
    private Long expenseId;
    // Contexte du paiement mensuel au moment de la transaction
    private BigDecimal totalHours;
    private BigDecimal hourlyRate;
    private BigDecimal totalAmount;
    private BigDecimal amountPaidAfter;
    private BigDecimal remainingAfter;

    public static TeacherPaymentTransactionResponse from(TeacherPaymentTransaction t) {
        var p = t.getMonthlyPayment();
        return TeacherPaymentTransactionResponse.builder()
                .id(t.getId())
                .monthlyPaymentId(p.getId())
                .teacherId(p.getTeacher().getId())
                .teacherName(p.getTeacher().getFullName())
                .monthDate(p.getMonthDate())
                .receiptNo(t.getReceiptNo())
                .amount(t.getAmount())
                .method(t.getMethod())
                .paymentDate(t.getPaymentDate())
                .reference(t.getReference())
                .receivedByName(t.getReceivedBy() != null ? t.getReceivedBy().getUsername() : null)
                .observation(t.getObservation())
                .expenseId(t.getExpenseId())
                .totalHours(p.getTotalHours())
                .hourlyRate(p.getHourlyRate())
                .totalAmount(p.getTotalAmount())
                .amountPaidAfter(p.getAmountPaid())
                .remainingAfter(p.getRemainingAmount())
                .build();
    }
}
