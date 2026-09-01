package com.school.dto.response;

import com.school.enums.LmdDecision;
import com.school.enums.Mention;
import com.school.enums.UniversityLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Données d'un relevé universitaire PDF.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LmdReleveResponse {

    private String studentName;
    private String matricule;
    private String fieldName;
    private String programName;
    private UniversityLevel level;
    private String academicYear;
    private String semester;
    private int session;
    private BigDecimal average;
    private LmdDecision decision;
    private Mention mention;
    private Integer creditsObtained;
    private Integer creditsFailed;
    private Integer totalCredits;
    @Builder.Default
    private List<UeLine> ues = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UeLine {
        private String code;
        private String name;
        private int coefficient;
        private int credits;
        private BigDecimal note;
    }
}
