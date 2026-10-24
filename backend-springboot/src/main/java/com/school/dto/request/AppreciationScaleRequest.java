package com.school.dto.request;

import com.school.enums.EducationCycle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** Création ou modification d'un barème d'appréciation. */
@Data
public class AppreciationScaleRequest {

    @NotBlank(message = "Le nom du barème est obligatoire")
    @Size(max = 100, message = "Le nom ne peut dépasser 100 caractères")
    private String name;

    @NotNull(message = "Le cycle scolaire est obligatoire")
    private EducationCycle cycle;

    @NotNull(message = "La note minimale de l'échelle est obligatoire")
    private BigDecimal minNote;

    @NotNull(message = "La note maximale de l'échelle est obligatoire")
    private BigDecimal maxNote;

    @NotEmpty(message = "Au moins un intervalle est obligatoire")
    @Size(max = 50, message = "Un barème ne peut pas dépasser 50 intervalles")
    @Valid
    private List<AppreciationBandRequest> bands;
}
