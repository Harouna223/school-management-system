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
public class TeacherHourlyRateRequest {

    @NotNull(message = "L'enseignant est obligatoire")
    private Long teacherId;

    @NotNull(message = "Le tarif horaire est obligatoire")
    @DecimalMin(value = "0.01", message = "Le tarif horaire doit être strictement positif")
    private BigDecimal hourlyRate;

    @NotNull(message = "La date de début de validité est obligatoire")
    private LocalDate startDate;

    /** Facultatif : tarif à durée indéterminée si absent. */
    private LocalDate endDate;

    /** Année scolaire (facultatif si une seule année en cours). */
    private Long academicYearId;
}
