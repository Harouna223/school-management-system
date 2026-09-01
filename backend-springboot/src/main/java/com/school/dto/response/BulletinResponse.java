package com.school.dto.response;

import com.school.entity.Bulletin;
import com.school.enums.DeliberationStatus;
import com.school.enums.Term;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulletinResponse {

    private Long id;
    private Long studentId;
    private String studentName;
    private String matricule;
    private String className;
    private Term term;
    private String academicYear;
    private BigDecimal average;
    private BigDecimal classAverage;
    private BigDecimal classMin;
    private BigDecimal classMax;
    private Integer rank;
    private String mention;
    private DeliberationStatus decision;
    private String pdfPath;

    public static BulletinResponse from(Bulletin b) {
        return BulletinResponse.builder()
                .id(b.getId())
                .studentId(b.getStudent().getId())
                .studentName(b.getStudent().getFullName())
                .matricule(b.getStudent().getMatricule())
                .className(b.getStudent().getSchoolClass() != null ? b.getStudent().getSchoolClass().getName() : null)
                .term(b.getTerm())
                .academicYear(b.getAcademicYear())
                .average(b.getAverage())
                .classAverage(b.getClassAverage())
                .classMin(b.getClassMin())
                .classMax(b.getClassMax())
                .rank(b.getRank())
                .mention(b.getMention())
                .decision(b.getDecision())
                .pdfPath(b.getPdfPath())
                .build();
    }
}