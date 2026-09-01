package com.school.dto.response;

import com.school.entity.Convocation;
import com.school.enums.ConvocationStatus;
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
    private Long studentId;
    private String studentName;
    private String matricule;
    private String context;
    private String subject;
    private String message;
    private LocalDate date;
    private LocalDateTime time;
    private String location;
    private ConvocationStatus status;
    private LocalDateTime createdAt;

    public static ConvocationResponse from(Convocation c) {
        return ConvocationResponse.builder()
                .id(c.getId())
                .reference(c.getReference())
                .studentId(c.getStudent().getId())
                .studentName(c.getStudent().getFirstName() + " " + c.getStudent().getLastName())
                .matricule(c.getStudent().getMatricule())
                .context(c.getContext())
                .subject(c.getSubject())
                .message(c.getMessage())
                .date(c.getDate())
                .time(c.getTime())
                .location(c.getLocation())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .build();
    }
}