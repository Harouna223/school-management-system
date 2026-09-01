package com.school.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Rapports universitaires : effectifs, réussite, crédits, dettes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UniversityReportResponse {

    private long totalEnrollments;
    private long totalActive;
    private long withDebts;
    private long totalDeliberations;
    private long totalExams;
    private long totalStages;
    private long totalConvocations;

    @Builder.Default
    private List<FieldStat> byField = new ArrayList<>();

    @Builder.Default
    private List<LevelStat> byLevel = new ArrayList<>();

    @Builder.Default
    private List<SuccessStat> successByField = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldStat {
        private String fieldName;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LevelStat {
        private String level;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuccessStat {
        private String fieldName;
        private long admis;
        private long ajournes;
        private BigDecimal successRate;
    }
}