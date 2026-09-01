package com.school.dto.response;

import com.school.entity.TeacherAttendance;
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
public class TeacherAttendanceResponse {

    private Long id;
    private Long teacherId;
    private String teacherName;
    private String employeeNo;
    private LocalDate date;
    private AttendanceStatus status;
    private String justification;

    public static TeacherAttendanceResponse from(TeacherAttendance a) {
        return TeacherAttendanceResponse.builder()
                .id(a.getId())
                .teacherId(a.getTeacher().getId())
                .teacherName(a.getTeacher().getFullName())
                .employeeNo(a.getTeacher().getEmployeeNo())
                .date(a.getDate())
                .status(a.getStatus())
                .justification(a.getJustification())
                .build();
    }
}