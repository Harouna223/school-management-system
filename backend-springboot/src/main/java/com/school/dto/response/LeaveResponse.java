package com.school.dto.response;

import com.school.entity.Leave;
import com.school.enums.LeaveStatus;
import com.school.enums.LeaveType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveResponse {

    private Long id;
    private Long teacherId;
    private String teacherName;
    private LeaveType type;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    private LeaveStatus status;

    public static LeaveResponse from(Leave l) {
        return LeaveResponse.builder()
                .id(l.getId())
                .teacherId(l.getTeacher().getId())
                .teacherName(l.getTeacher().getFullName())
                .type(l.getType())
                .startDate(l.getStartDate())
                .endDate(l.getEndDate())
                .reason(l.getReason())
                .status(l.getStatus())
                .build();
    }
}