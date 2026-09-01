package com.school.dto.response;

import com.school.entity.Payroll;
import com.school.enums.PayrollStatus;
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
public class PayrollResponse {

    private Long id;
    private Long teacherId;
    private String teacherName;
    private LocalDate monthDate;
    private BigDecimal baseSalary;
    private BigDecimal allowances;
    private BigDecimal deductions;
    private BigDecimal netSalary;
    private PayrollStatus status;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;

    public static PayrollResponse from(Payroll p) {
        return PayrollResponse.builder()
                .id(p.getId())
                .teacherId(p.getTeacher().getId())
                .teacherName(p.getTeacher().getFullName())
                .monthDate(p.getMonthDate())
                .baseSalary(p.getBaseSalary())
                .allowances(p.getAllowances())
                .deductions(p.getDeductions())
                .netSalary(p.getNetSalary())
                .status(p.getStatus())
                .paidAt(p.getPaidAt())
                .createdAt(p.getCreatedAt())
                .build();
    }
}