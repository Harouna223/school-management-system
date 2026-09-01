package com.school.dto.response;

import com.school.entity.Attendance;
import com.school.enums.AttendanceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceResponse {

    private Long id;
    private Long studentId;
    private String studentName;
    private String matricule;
    private Long classId;
    private LocalDate date;
    private AttendanceStatus status;
    private String justification;

    public static AttendanceResponse from(Attendance a) {
        return AttendanceResponse.builder()
                .id(a.getId())
                .studentId(a.getStudent().getId())
                .studentName(a.getStudent().getFullName())
                .matricule(a.getStudent().getMatricule())
                .classId(a.getSchoolClass().getId())
                .date(a.getDate())
                .status(a.getStatus())
                .justification(a.getJustification())
                .build();
    }
}