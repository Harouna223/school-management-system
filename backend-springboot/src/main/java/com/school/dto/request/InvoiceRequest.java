package com.school.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceRequest {

    @NotNull(message = "L'élève est obligatoire")
    private Long studentId;

    @NotNull(message = "Le type de frais est obligatoire")
    private Long feeTypeId;

    private java.math.BigDecimal amount;

    /** Remise éventuelle appliquée sur le montant de la facture. */
    private java.math.BigDecimal discount;

    @NotNull(message = "La date d'échéance est obligatoire")
    private LocalDate dueDate;
}