package com.school.repository;

import com.school.entity.TeacherAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TeacherAttendanceRepository extends JpaRepository<TeacherAttendance, Long> {

    List<TeacherAttendance> findByDateOrderByTeacherLastNameAsc(LocalDate date);

    Optional<TeacherAttendance> findByTeacherIdAndDate(Long teacherId, LocalDate date);


    /**
     * Garde anti-cascade : compte les enregistrements qui seraient detruits
     * silencieusement avec le parent supprime.
     */
    long countByTeacherId(Long teacherId);
}
