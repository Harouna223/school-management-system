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
 * Création / modification d'un stage universitaire.
 *
 * <p>Remplace l'entité {@code Stage} en corps de requête : seuls les champs
 * métier sont acceptés (ni identifiant ni date de création imposés par le
 * client) et les bornes de notation sont vérifiées ici.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StageRequest {

    @NotNull(message = "L'étudiant est obligatoire")
    private Long studentId;

    @NotBlank(message = "L'entreprise est obligatoire")
    @Size(max = 150, message = "Le nom de l'entreprise ne peut pas dépasser 150 caractères")
    private String company;

    @Size(max = 150, message = "Le contact entreprise ne peut pas dépasser 150 caractères")
    private String companyContact;

    @Size(max = 200, message = "Le sujet ne peut pas dépasser 200 caractères")
    private String subject;

    private LocalDate startDate;

    private LocalDate endDate;

    private Long supervisorId;

    @Size(max = 50, message = "La référence de convention ne peut pas dépasser 50 caractères")
    private String conventionRef;

    private boolean reportSubmitted;

    private LocalDate defenseDate;

    @DecimalMin(value = "0", message = "La note ne peut pas être négative")
    @DecimalMax(value = "20", message = "La note ne peut pas dépasser 20")
    private BigDecimal grade;

    private String evaluation;

    /** EN_COURS, TERMINE, SOUTENU ou ABANDONNE. */
    @Size(max = 20, message = "Le statut ne peut pas dépasser 20 caractères")
    private String status;
}
