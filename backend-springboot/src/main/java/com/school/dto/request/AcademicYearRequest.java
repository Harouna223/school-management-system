package com.school.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Création / modification d'une année scolaire.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcademicYearRequest {

    @NotBlank(message = "Le libellé est obligatoire")
    @Size(max = 50, message = "Libellé trop long (50 caractères max)")
    private String label;

    private LocalDate startDate;

    private LocalDate endDate;

    private Boolean isCurrent;
}
