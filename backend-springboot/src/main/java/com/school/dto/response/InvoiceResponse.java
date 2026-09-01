package com.school.dto.response;

import com.school.entity.Invoice;
import com.school.enums.InvoiceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceResponse {

    private Long id;
    private String invoiceNo;
    private Long studentId;
    private String studentName;
    private String matricule;
    private String studentPhone;
    private String parentPhone;
    private Long feeTypeId;
    private String feeTypeName;
    private BigDecimal amount;
    private BigDecimal discount;
    private BigDecimal paidAmount;
    private BigDecimal remainingAmount;
    private LocalDate dueDate;
    private InvoiceStatus status;

    public static InvoiceResponse from(Invoice i) {
        BigDecimal discount = i.getDiscount() != null ? i.getDiscount() : BigDecimal.ZERO;
        return InvoiceResponse.builder()
                .id(i.getId())
                .invoiceNo(i.getInvoiceNo())
                .studentId(i.getStudent().getId())
                .studentName(i.getStudent().getFullName())
                .matricule(i.getStudent().getMatricule())
                .studentPhone(i.getStudent().getPhone())
                .parentPhone(i.getStudent().getParent() != null ? i.getStudent().getParent().getPhone() : null)
                .feeTypeId(i.getFeeType().getId())
                .feeTypeName(i.getFeeType().getName())
                .amount(i.getAmount())
                .discount(discount)
                .paidAmount(i.getPaidAmount())
                .remainingAmount(i.getAmount().subtract(discount).subtract(i.getPaidAmount()))
                .dueDate(i.getDueDate())
                .status(i.getStatus())
                .build();
    }
}