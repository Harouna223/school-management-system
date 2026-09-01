package com.school.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradeRequest {

    @NotNull(message = "L'élève est obligatoire")
    private Long studentId;

    @NotNull(message = "L'évaluation est obligatoire")
    private Long examId;

    @NotNull(message = "La note est obligatoire")
    @DecimalMin(value = "0.0", message = "La note minimale est 0")
    @DecimalMax(value = "20.0", message = "La note maximale est 20")
    private BigDecimal value;

    private BigDecimal maxValue;
    private String appreciation;
}