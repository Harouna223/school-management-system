package com.school.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
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
public class PayrollGenerateRequest {

    @NotNull(message = "L'enseignant est obligatoire")
    private Long teacherId;

    @NotNull(message = "Le mois est obligatoire (1er jour du mois)")
    private LocalDate monthDate;

    @DecimalMin(value = "0.0", message = "Les primes doivent être positives")
    private BigDecimal allowances;

    @DecimalMin(value = "0.0", message = "Les retenues doivent être positives")
    private BigDecimal deductions;
}