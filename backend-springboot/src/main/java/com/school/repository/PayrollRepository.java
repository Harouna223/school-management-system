package com.school.repository;

import com.school.entity.Payroll;
import com.school.enums.PayrollStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {

    Optional<Payroll> findByTeacherIdAndMonthDate(Long teacherId, LocalDate monthDate);

    List<Payroll> findByTeacherIdOrderByMonthDateDesc(Long teacherId);

    List<Payroll> findByMonthDateOrderByTeacherLastNameAsc(LocalDate monthDate);

    List<Payroll> findByStatus(PayrollStatus status);

    @Query("SELECT p FROM Payroll p WHERE (:teacherId IS NULL OR p.teacher.id = :teacherId) AND (:monthDate IS NULL OR p.monthDate = :monthDate) AND (:status IS NULL OR p.status = :status)")
    List<Payroll> search(@Param("teacherId") Long teacherId,
                         @Param("monthDate") LocalDate monthDate,
                         @Param("status") PayrollStatus status);
}