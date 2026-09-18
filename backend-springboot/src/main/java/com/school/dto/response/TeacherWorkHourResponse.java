package com.school.dto.response;

import com.school.entity.TeacherWorkHour;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherWorkHourResponse {

    private Long id;
    private Long teacherId;
    private String teacherName;
    private LocalDate date;
    private BigDecimal hours;
    private Long subjectId;
    private String subjectName;
    private Long classId;
    private String className;
    private BigDecimal hourlyRateApplied;
    private BigDecimal amount;
    private String observation;
    private String createdByName;
    private LocalDateTime createdAt;

    public static TeacherWorkHourResponse from(TeacherWorkHour w) {
        return TeacherWorkHourResponse.builder()
                .id(w.getId())
                .teacherId(w.getTeacher().getId())
                .teacherName(w.getTeacher().getFullName())
                .date(w.getDate())
                .hours(w.getHours())
                .subjectId(w.getSubject() != null ? w.getSubject().getId() : null)
                .subjectName(w.getSubject() != null ? w.getSubject().getName() : null)
                .classId(w.getSchoolClass() != null ? w.getSchoolClass().getId() : null)
                .className(w.getSchoolClass() != null ? w.getSchoolClass().getName() : null)
                .hourlyRateApplied(w.getHourlyRateApplied())
                .amount(w.getAmount())
                .observation(w.getObservation())
                .createdByName(w.getCreatedBy() != null ? w.getCreatedBy().getUsername() : null)
                .createdAt(w.getCreatedAt())
                .build();
    }
}
