package com.school.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Saisie d'une note UE.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UeGradeRequest {

    @NotNull(message = "L'élève est obligatoire")
    private Long studentId;

    @NotNull(message = "L'UE est obligatoire")
    private Long ueId;

    @NotNull(message = "Le semestre est obligatoire")
    private String semester;

    /** Session : 1 = normale, 2 = rattrapage. */
    @Builder.Default
    private int session = 1;

    @NotNull(message = "La note est obligatoire")
    private BigDecimal value;

    private String appreciation;
}
