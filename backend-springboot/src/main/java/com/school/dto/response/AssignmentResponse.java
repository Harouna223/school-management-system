package com.school.dto.response;

import com.school.entity.SubjectAssignment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Affectation enseignant -> matière -> classe.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentResponse {

    private Long id;
    private Long teacherId;
    private String teacherName;
    private Long subjectId;
    private String subjectName;
    private Long classId;
    private String className;

    public static AssignmentResponse from(SubjectAssignment a) {
        return AssignmentResponse.builder()
                .id(a.getId())
                .teacherId(a.getTeacher().getId())
                .teacherName(a.getTeacher().getFullName())
                .subjectId(a.getSubject().getId())
                .subjectName(a.getSubject().getName())
                .classId(a.getSchoolClass().getId())
                .className(a.getSchoolClass().getName())
                .build();
    }
}