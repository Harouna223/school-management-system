package com.school.dto.request;

import com.school.enums.EnrollmentStatus;
import com.school.enums.UniversityLevel;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inscription d'un élève dans une filière LMD.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LmdEnrollmentRequest {

    @NotNull(message = "L'élève est obligatoire")
    private Long studentId;

    @NotNull(message = "La filière est obligatoire")
    private Long fieldId;

    @NotNull(message = "Le semestre est obligatoire")
    private String currentSemester;

    private UniversityLevel level;
    private Long programId;
    private String academicYear;
    private EnrollmentStatus enrollmentStatus;
}