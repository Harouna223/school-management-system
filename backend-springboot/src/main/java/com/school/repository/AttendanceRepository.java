package com.school.repository;

import com.school.entity.Attendance;
import com.school.enums.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByStudentIdAndDate(Long studentId, LocalDate date);

    List<Attendance> findBySchoolClassIdAndDate(Long classId, LocalDate date);

    List<Attendance> findBySchoolClassIdAndDateBetween(Long classId, LocalDate from, LocalDate to);

    List<Attendance> findByStudentId(Long studentId);

    long countByStudentIdAndStatus(Long studentId, AttendanceStatus status);

    long countBySchoolClassIdAndDateAndStatus(Long classId, LocalDate date, AttendanceStatus status);

    long countByDate(LocalDate date);

    long countByDateAndStatus(LocalDate date, AttendanceStatus status);

    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.date = :date AND a.status = :status "
            + "AND (:cycle IS NULL OR a.student.educationCycle = :cycle)")
    long countByDateAndStatusAndCycle(@Param("date") LocalDate date,
                                      @Param("status") AttendanceStatus status,
                                      @Param("cycle") com.school.enums.EducationCycle cycle);
}
