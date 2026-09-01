package com.school.dto.request;

import com.school.enums.ExamStatus;
import com.school.enums.ExamType;
import com.school.enums.Term;
import jakarta.validation.constraints.NotBlank;
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
public class ExamRequest {

    @NotBlank(message = "Le nom de l'évaluation est obligatoire")
    private String name;

    @NotNull(message = "Le type est obligatoire")
    private ExamType type;

    @NotNull(message = "Le trimestre est obligatoire")
    private Term term;

    @NotBlank(message = "L'année académique est obligatoire")
    private String academicYear;

    @NotNull(message = "La classe est obligatoire")
    private Long classId;

    @NotNull(message = "La matière est obligatoire")
    private Long subjectId;

    private LocalDate examDate;
    private Integer coefficient;
    private ExamStatus status;
}