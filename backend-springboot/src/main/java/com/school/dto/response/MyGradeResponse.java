package com.school.dto.response;

import com.school.entity.Grade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Note d'un élève dans son espace personnel (avec trimestre et évaluation).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyGradeResponse {

    private Long id;
    private Long examId;
    private String examName;
    private String examType;
    private Long subjectId;
    private String subjectName;
    private String term;
    private String academicYear;
    private BigDecimal value;
    private BigDecimal maxValue;
    private Integer coefficient;
    private String appreciation;
    private LocalDate examDate;

    public static MyGradeResponse from(Grade g) {
        return MyGradeResponse.builder()
                .id(g.getId())
                .examId(g.getExam().getId())
                .examName(g.getExam().getName())
                .examType(g.getExam().getType() != null ? g.getExam().getType().name() : null)
                .subjectId(g.getExam().getSubject().getId())
                .subjectName(g.getExam().getSubject().getName())
                .term(g.getExam().getTerm() != null ? g.getExam().getTerm().name() : null)
                .academicYear(g.getExam().getAcademicYear())
                .value(g.getValue())
                .maxValue(g.getMaxValue())
                .coefficient(g.getExam().getCoefficient())
                .appreciation(g.getAppreciation())
                .examDate(g.getExam().getExamDate())
                .build();
    }
}