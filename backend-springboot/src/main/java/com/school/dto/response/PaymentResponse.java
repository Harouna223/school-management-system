package com.school.dto.response;

import com.school.entity.Payment;
import com.school.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private Long id;
    private String receiptNo;
    private Long invoiceId;
    private String invoiceNo;
    private Long studentId;
    private String studentName;
    private String matricule;
    private BigDecimal amount;
    private PaymentMethod method;
    private LocalDateTime paymentDate;
    private String recordedByName;
    private String note;

    public static PaymentResponse from(Payment p) {
        return PaymentResponse.builder()
                .id(p.getId())
                .receiptNo(p.getReceiptNo())
                .invoiceId(p.getInvoice().getId())
                .invoiceNo(p.getInvoice().getInvoiceNo())
                .studentId(p.getStudent().getId())
                .studentName(p.getStudent().getFullName())
                .matricule(p.getStudent().getMatricule())
                .amount(p.getAmount())
                .method(p.getMethod())
                .paymentDate(p.getPaymentDate())
                .recordedByName(p.getRecordedBy() != null
                        ? p.getRecordedBy().getFirstName() + " " + p.getRecordedBy().getLastName() : null)
                .note(p.getNote())
                .build();
    }
}