package com.school.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Prévisualisation d'un barème : le libellé obtenu pour des notes d'exemple.
 *
 * <p>Permet à l'administrateur de vérifier les frontières (12,00 vs 11,99)
 * et les décimales (9,5 ; 12,75 ; 17,25) AVANT d'activer le barème.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppreciationPreviewResponse {

    private String scaleName;
    private String cycle;
    private BigDecimal minNote;
    private BigDecimal maxNote;
    private List<Sample> samples;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Sample {
        private BigDecimal note;
        private String label;
        private String observation;
    }
}
