package com.school.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeTypeRequest {

    @NotBlank(message = "Le nom est obligatoire")
    private String name;

    @NotNull(message = "Le montant est obligatoire")
    private BigDecimal amount;

    private String description;
}