package com.school.dto.response;

import com.school.entity.Contract;
import com.school.enums.ContractStatus;
import com.school.enums.ContractType;
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
public class ContractResponse {

    private Long id;
    private Long teacherId;
    private String teacherName;
    private ContractType type;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal baseSalary;
    private String description;
    private ContractStatus status;
    private LocalDateTime createdAt;

    public static ContractResponse from(Contract c) {
        return ContractResponse.builder()
                .id(c.getId())
                .teacherId(c.getTeacher().getId())
                .teacherName(c.getTeacher().getFullName())
                .type(c.getType())
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .baseSalary(c.getBaseSalary())
                .description(c.getDescription())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .build();
    }
}