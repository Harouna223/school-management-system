package com.school.dto.response;

import com.school.entity.TeacherHourlyRate;
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
public class TeacherHourlyRateResponse {

    private Long id;
    private Long teacherId;
    private String teacherName;
    private BigDecimal hourlyRate;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long academicYearId;
    private String academicYearLabel;
    private boolean active;
    private LocalDateTime createdAt;

    public static TeacherHourlyRateResponse from(TeacherHourlyRate r) {
        return TeacherHourlyRateResponse.builder()
                .id(r.getId())
                .teacherId(r.getTeacher().getId())
                .teacherName(r.getTeacher().getFullName())
                .hourlyRate(r.getHourlyRate())
                .startDate(r.getStartDate())
                .endDate(r.getEndDate())
                .academicYearId(r.getAcademicYear() != null ? r.getAcademicYear().getId() : null)
                .academicYearLabel(r.getAcademicYear() != null ? r.getAcademicYear().getLabel() : null)
                .active(r.isActive())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
