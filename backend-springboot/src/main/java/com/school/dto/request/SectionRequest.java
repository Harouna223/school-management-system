package com.school.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SectionRequest(
        @NotBlank(message = "Le nom est obligatoire") @Size(max = 100) String name,
        @Size(max = 255) String description) {
}
