package com.school.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Création / modification d'un mémoire universitaire.
 *
 * <p>Remplace l'entité {@code Memoire} en corps de requête (voir
 * {@link StageRequest}).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoireRequest {

    @NotNull(message = "L'étudiant est obligatoire")
    private Long studentId;

    @NotBlank(message = "Le sujet est obligatoire")
    @Size(max = 200, message = "Le sujet ne peut pas dépasser 200 caractères")
    private String subject;

    private Long directorId;

    private LocalDate defenseDate;

    @Size(max = 100, message = "Le jury ne peut pas dépasser 100 caractères")
    private String jury;

    @Size(max = 150, message = "Le lieu de soutenance ne peut pas dépasser 150 caractères")
    private String defenseLocation;

    @DecimalMin(value = "0", message = "La note ne peut pas être négative")
    @DecimalMax(value = "20", message = "La note ne peut pas dépasser 20")
    private BigDecimal grade;

    private String decision;

    /** EN_REDACTION, SOUMIS, SOUTENU, VALIDEE ou REFUSE. */
    @Size(max = 20, message = "Le statut ne peut pas dépasser 20 caractères")
    private String status;
}
