package com.school.dto.response;

import com.school.entity.AppreciationBand;
import com.school.entity.AppreciationScale;
import com.school.enums.EducationCycle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Barème d'appréciation renvoyé à l'interface d'administration. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppreciationScaleResponse {

    private Long id;
    private String name;
    private EducationCycle cycle;
    private BigDecimal minNote;
    private BigDecimal maxNote;
    private int version;
    private boolean active;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Band> bands;

    /** Un intervalle, tel qu'affiché et édité. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Band {
        private Long id;
        private BigDecimal lowerBound;
        private BigDecimal upperBound;
        private String label;
        private String observation;
        private int sortOrder;
    }

    public static AppreciationScaleResponse from(AppreciationScale scale) {
        return AppreciationScaleResponse.builder()
                .id(scale.getId())
                .name(scale.getName())
                .cycle(scale.getCycle())
                .minNote(scale.getMinNote())
                .maxNote(scale.getMaxNote())
                .version(scale.getVersion())
                .active(scale.isActive())
                .createdByName(scale.getCreatedBy() != null
                        ? (scale.getCreatedBy().getFirstName() + " " + scale.getCreatedBy().getLastName()).trim()
                        : null)
                .createdAt(scale.getCreatedAt())
                .updatedAt(scale.getUpdatedAt())
                .bands(scale.getBands().stream()
                        .map(band -> Band.builder()
                                .id(band.getId())
                                .lowerBound(band.getLowerBound())
                                .upperBound(band.getUpperBound())
                                .label(band.getLabel())
                                .observation(band.getObservation())
                                .sortOrder(band.getSortOrder())
                                .build())
                        .toList())
                .build();
    }

    /** Conversion utilitaire d'une entité intervalle. */
    public static Band band(AppreciationBand band) {
        return Band.builder()
                .id(band.getId())
                .lowerBound(band.getLowerBound())
                .upperBound(band.getUpperBound())
                .label(band.getLabel())
                .observation(band.getObservation())
                .sortOrder(band.getSortOrder())
                .build();
    }
}
