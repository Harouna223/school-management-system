package com.school.repository;

import com.school.entity.TeacherMonthClosure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface TeacherMonthClosureRepository extends JpaRepository<TeacherMonthClosure, Long> {

    Optional<TeacherMonthClosure> findByMonthDate(LocalDate monthDate);

    boolean existsByMonthDateAndClosedTrue(LocalDate monthDate);
}
