package com.school.repository;

import com.school.entity.Schedule;
import com.school.enums.DayOfWeek;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalTime;
import java.util.List;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    List<Schedule> findBySchoolClassId(Long classId);

    List<Schedule> findByTeacherId(Long teacherId);

    List<Schedule> findByRoomId(Long roomId);

    List<Schedule> findByDayOfWeekAndStartTimeLessThanAndEndTimeGreaterThan(
            DayOfWeek dayOfWeek, LocalTime endTime, LocalTime startTime);

    List<Schedule> findByDayOfWeek(DayOfWeek dayOfWeek);

    /**
     * Détection de conflits : créneaux d'une salle, classe ou enseignant.
     */
    default boolean isConflict(DayOfWeek day, LocalTime start, LocalTime end,
                               Long classId, Long teacherId, Long roomId, Long excludeId) {
        List<Schedule> candidates = findByDayOfWeek(day).stream()
                .filter(s -> !s.getId().equals(excludeId))
                .filter(s -> start.isBefore(s.getEndTime()) && end.isAfter(s.getStartTime()))
                .toList();
        return candidates.stream().anyMatch(s ->
                s.getSchoolClass().getId().equals(classId)
                || s.getTeacher().getId().equals(teacherId)
                || (roomId != null && s.getRoom() != null && s.getRoom().getId().equals(roomId)));
    }
}
