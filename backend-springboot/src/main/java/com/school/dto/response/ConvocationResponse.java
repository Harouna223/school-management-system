package com.school.dto.response;

import com.school.entity.Convocation;
import com.school.enums.ConvocationStatus;
import com.school.enums.ConvocationTarget;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConvocationResponse {

    private Long id;
    private String reference;
    /** ÉLÈVE, PARENT ou ENSEIGNANT : qui est convoqué. */
    private ConvocationTarget target;
    private Long studentId;
    private String studentName;
    private String matricule;
    private Long parentId;
    private String parentName;
    private Long teacherId;
    private String teacherName;
    private String context;
    private String subject;
    private String message;
    private LocalDate date;
    private LocalDateTime time;
    private String location;
    private ConvocationStatus status;
    private LocalDateTime createdAt;

    /**
     * Convertit une convocation. Les rattachements absents (une convocation de
     * parent n'a pas d'élève, par exemple) restent {@code null} : c'est ce qui
     * distingue les trois cibles côté interface.
     */
    public static ConvocationResponse from(Convocation c) {
        ConvocationResponseBuilder builder = ConvocationResponse.builder()
                .id(c.getId())
                .reference(c.getReference())
                .target(c.getTarget())
                .context(c.getContext())
                .subject(c.getSubject())
                .message(c.getMessage())
                .date(c.getDate())
                .time(c.getTime())
                .location(c.getLocation())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt());
        if (c.getStudent() != null) {
            builder.studentId(c.getStudent().getId())
                    .studentName(c.getStudent().getFirstName() + " " + c.getStudent().getLastName())
                    .matricule(c.getStudent().getMatricule());
        }
        if (c.getParent() != null) {
            builder.parentId(c.getParent().getId())
                    .parentName(c.getParent().getFirstName() + " " + c.getParent().getLastName());
        }
        if (c.getTeacher() != null) {
            builder.teacherId(c.getTeacher().getId())
                    .teacherName(c.getTeacher().getFirstName() + " " + c.getTeacher().getLastName());
        }
        return builder.build();
    }
}