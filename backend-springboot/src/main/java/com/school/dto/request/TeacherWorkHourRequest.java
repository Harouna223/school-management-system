package com.school.dto.request;

import jakarta.validation.constraints.DecimalMax;
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
public class TeacherWorkHourRequest {

    @NotNull(message = "L'enseignant est obligatoire")
    private Long teacherId;

    @NotNull(message = "La date est obligatoire")
    private LocalDate date;

    @NotNull(message = "Le nombre d'heures est obligatoire")
    @DecimalMin(value = "0.01", message = "Le nombre d'heures doit être strictement positif")
    @DecimalMax(value = "24", message = "Le nombre d'heures ne peut pas dépasser 24 h")
    private BigDecimal hours;

    /** Matière (facultative). */
    private Long subjectId;

    /** Classe (facultative). */
    private Long classId;

    @Deprecated
    private Long academicYearId;

    private String observation;
}
