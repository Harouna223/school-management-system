package com.school.dto.response;

import com.school.entity.Grade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradeResponse {

    private Long id;
    private Long studentId;
    private String studentName;
    private String matricule;
    private Long examId;
    private String examName;
    private Long subjectId;
    private String subjectName;
    private BigDecimal value;
    private BigDecimal maxValue;
    private String appreciation;

    public static GradeResponse from(Grade g) {
        return GradeResponse.builder()
                .id(g.getId())
                .studentId(g.getStudent().getId())
                .studentName(g.getStudent().getFullName())
                .matricule(g.getStudent().getMatricule())
                .examId(g.getExam().getId())
                .examName(g.getExam().getName())
                .subjectId(g.getExam().getSubject().getId())
                .subjectName(g.getExam().getSubject().getName())
                .value(g.getValue())
                .maxValue(g.getMaxValue())
                .appreciation(g.getAppreciation())
                .build();
    }
}