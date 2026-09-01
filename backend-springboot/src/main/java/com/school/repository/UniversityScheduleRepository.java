package com.school.repository;

import com.school.entity.UniversitySchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UniversityScheduleRepository extends JpaRepository<UniversitySchedule, Long> {

    List<UniversitySchedule> findBySemesterOrderByDayOfWeekAscStartTimeAsc(String semester);

    List<UniversitySchedule> findByCourseUnitUeFieldIdOrderByDayOfWeekAscStartTimeAsc(Long fieldId);

    List<UniversitySchedule> findByCourseUnitIdOrderByDayOfWeekAscStartTimeAsc(Long courseUnitId);
}