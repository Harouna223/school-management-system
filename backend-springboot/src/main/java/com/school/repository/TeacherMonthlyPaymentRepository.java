package com.school.repository;

import com.school.entity.TeacherMonthlyPayment;
import com.school.enums.TeacherPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TeacherMonthlyPaymentRepository extends JpaRepository<TeacherMonthlyPayment, Long> {

    Optional<TeacherMonthlyPayment> findByTeacherIdAndMonthDate(Long teacherId, LocalDate monthDate);

    List<TeacherMonthlyPayment> findByMonthDateOrderByTeacherLastNameAsc(LocalDate monthDate);

    @Query("""
            SELECT p FROM TeacherMonthlyPayment p
            WHERE (:teacherId IS NULL OR p.teacher.id = :teacherId)
              AND (:monthDate IS NULL OR p.monthDate = :monthDate)
              AND (:status IS NULL OR p.status = :status)
              AND (:yearId IS NULL OR p.academicYear.id = :yearId)
            ORDER BY p.monthDate DESC, p.teacher.lastName ASC
            """)
    List<TeacherMonthlyPayment> search(@Param("teacherId") Long teacherId,
                                       @Param("monthDate") LocalDate monthDate,
                                       @Param("status") TeacherPaymentStatus status,
                                       @Param("yearId") Long yearId);
}
