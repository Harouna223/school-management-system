package com.school.dto.request;

import com.school.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Paiement (total ou partiel) du salaire mensuel d'un enseignant.
 * Le montant est toujours contrôlé côté serveur : il ne peut jamais
 * dépasser le reste à payer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherPayRequest {

    @NotNull(message = "L'enseignant est obligatoire")
    private Long teacherId;

    /** Premier jour du mois concerné (ex. 2026-09-01). */
    @NotNull(message = "Le mois est obligatoire")
    private LocalDate monthDate;

    @NotNull(message = "Le montant est obligatoire")
    @DecimalMin(value = "0.01", message = "Le montant doit être strictement positif")
    private BigDecimal amount;

    @NotNull(message = "Le mode de paiement est obligatoire")
    private PaymentMethod method;

    private String reference;

    private String observation;
}
