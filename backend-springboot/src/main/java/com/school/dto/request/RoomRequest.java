package com.school.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoomRequest(
        @NotBlank(message = "Le nom est obligatoire") @Size(max = 100) String name,
        @Min(value = 1, message = "La capacité doit être positive") Integer capacity,
        @Size(max = 255) String location) {
}
