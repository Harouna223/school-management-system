package com.school.dto.response;

import com.school.entity.Exam;
import com.school.enums.ExamStatus;
import com.school.enums.ExamType;
import com.school.enums.Term;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamResponse {

    private Long id;
    private String name;
    private ExamType type;
    private Term term;
    private String academicYear;
    private Long classId;
    private String className;
    private Long subjectId;
    private String subjectName;
    private LocalDate examDate;
    private Integer coefficient;
    private ExamStatus status;
    private long gradeCount;

    public static ExamResponse from(Exam e) {
        return ExamResponse.builder()
                .id(e.getId())
                .name(e.getName())
                .type(e.getType())
                .term(e.getTerm())
                .academicYear(e.getAcademicYear())
                .classId(e.getSchoolClass().getId())
                .className(e.getSchoolClass().getName())
                .subjectId(e.getSubject().getId())
                .subjectName(e.getSubject().getName())
                .examDate(e.getExamDate())
                .coefficient(e.getCoefficient())
                .status(e.getStatus())
                .build();
    }
}