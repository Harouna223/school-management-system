package com.school.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Statistiques globales du tableau de bord.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    private long totalStudents;
    private long totalTeachers;
    private long totalClasses;
    private long totalSubjects;
    private long activeStudents;

    private double studentsPerClassAvg;
    private long unpaidInvoices;
    private long totalPaymentsCount;
    private java.math.BigDecimal monthlyRevenue;
    private java.math.BigDecimal monthlyExpenses;
    private java.math.BigDecimal totalRevenue;
    private java.math.BigDecimal totalExpenses;

    private long todayPresent;
    private long todayAbsent;
    private long totalLate;

    private long totalBooks;
    private long borrowedBooks;
    private long pendingLeaves;

    private List<ChartPoint> revenueByMonth;
    private List<ChartPoint> studentsByClass;
    private List<ChartPoint> studentsByGender;
    private List<ChartPoint> averageBySubject;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartPoint {
        private String label;
        private Number value;
    }
}
