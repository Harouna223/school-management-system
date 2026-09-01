package com.school.repository;

import com.school.entity.UniversityAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface UniversityAttendanceRepository extends JpaRepository<UniversityAttendance, Long> {

    Optional<UniversityAttendance> findByEcIdAndStudentIdAndDateAndSessionType(
            Long ecId, Long studentId, LocalDate date, com.school.entity.UniversitySchedule.SessionType sessionType);

    List<UniversityAttendance> findByEcIdAndDateAndSessionTypeOrderByStudentId(
            Long ecId, LocalDate date, com.school.entity.UniversitySchedule.SessionType sessionType);

    List<UniversityAttendance> findByStudentIdOrderByDateDesc(Long studentId);

    List<UniversityAttendance> findByEcIdOrderByDateDesc(Long ecId);
}