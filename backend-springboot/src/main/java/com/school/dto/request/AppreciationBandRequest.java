package com.school.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** Un intervalle d'appréciation : [lowerBound, upperBound). */
@Data
public class AppreciationBandRequest {

    @NotNull(message = "La borne basse est obligatoire")
    private BigDecimal lowerBound;

    @NotNull(message = "La borne haute est obligatoire")
    private BigDecimal upperBound;

    @NotBlank(message = "Le libellé de l'appréciation est obligatoire")
    @Size(max = 60, message = "Le libellé ne peut dépasser 60 caractères")
    private String label;

    @Size(max = 255, message = "L'observation ne peut dépasser 255 caractères")
    private String observation;
}
