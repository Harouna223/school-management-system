package com.school.dto.request;

import com.school.enums.EducationCycle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LevelRequest(
        @NotBlank(message = "Le nom est obligatoire") @Size(max = 100) String name,
        @NotBlank(message = "Le code est obligatoire") @Size(max = 20) String code,
        EducationCycle educationCycle) {
}
