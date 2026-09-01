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
public class ClassRequest {

    @NotBlank(message = "Le nom de la classe est obligatoire")
    private String name;

    @NotBlank(message = "Le code est obligatoire")
    private String code;

    @NotNull(message = "Le niveau est obligatoire")
    private Long levelId;

    private Long sectionId;
    private Long roomId;
    private Integer capacity;
}