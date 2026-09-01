package com.school.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectRequest {

    @NotBlank(message = "Le nom de la matière est obligatoire")
    private String name;

    @NotBlank(message = "Le code est obligatoire")
    private String code;

    @NotNull(message = "Le coefficient est obligatoire")
    private Integer coefficient;

    private String description;
}