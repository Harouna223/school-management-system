package com.school.dto.response;

import com.school.entity.SubjectAssignment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Classe + matière enseignée par un enseignant (espace enseignant).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyTeacherClassResponse {

    private Long assignmentId;
    private Long classId;
    private String className;
    private Long subjectId;
    private String subjectName;

    public static MyTeacherClassResponse from(SubjectAssignment sa) {
        return MyTeacherClassResponse.builder()
                .assignmentId(sa.getId())
                .classId(sa.getSchoolClass().getId())
                .className(sa.getSchoolClass().getName())
                .subjectId(sa.getSubject().getId())
                .subjectName(sa.getSubject().getName())
                .build();
    }
}