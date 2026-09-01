package com.school.dto.request;

import com.school.enums.DayOfWeek;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleRequest {

    @NotNull(message = "Le jour est obligatoire")
    private DayOfWeek dayOfWeek;

    @NotNull(message = "L'heure de début est obligatoire")
    private LocalTime startTime;

    @NotNull(message = "L'heure de fin est obligatoire")
    private LocalTime endTime;

    @NotNull(message = "La classe est obligatoire")
    private Long classId;

    @NotNull(message = "La matière est obligatoire")
    private Long subjectId;

    @NotNull(message = "L'enseignant est obligatoire")
    private Long teacherId;

    private Long roomId;
}