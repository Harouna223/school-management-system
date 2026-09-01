package com.school.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Statistiques du tableau de bord universitaire.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UniversityDashboardResponse {

    private long totalEnrollments;
    private long totalFields;
    private long totalPrograms;
    private long totalUes;
    private long totalTeachers;
    private long activeStudents;
    private long admisCount;
    private long ajournesCount;
    private double successRate;
    @Builder.Default
    private List<ChartPoint> byLevel = List.of();
    @Builder.Default
    private List<ChartPoint> byField = List.of();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartPoint {
        private String label;
        private Number value;
    }
}
