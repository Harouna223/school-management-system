package com.school.dto.response;

import com.school.entity.Schedule;
import com.school.enums.DayOfWeek;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleResponse {

    private Long id;
    private DayOfWeek dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private Long classId;
    private String className;
    private Long subjectId;
    private String subjectName;
    private Long teacherId;
    private String teacherName;
    private Long roomId;
    private String roomName;

    public static ScheduleResponse from(Schedule s) {
        return ScheduleResponse.builder()
                .id(s.getId())
                .dayOfWeek(s.getDayOfWeek())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .classId(s.getSchoolClass().getId())
                .className(s.getSchoolClass().getName())
                .subjectId(s.getSubject().getId())
                .subjectName(s.getSubject().getName())
                .teacherId(s.getTeacher().getId())
                .teacherName(s.getTeacher().getFullName())
                .roomId(s.getRoom() != null ? s.getRoom().getId() : null)
                .roomName(s.getRoom() != null ? s.getRoom().getName() : null)
                .build();
    }
}