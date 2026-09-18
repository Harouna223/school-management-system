package com.school.repository;

import com.school.entity.TeacherHourlyRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TeacherHourlyRateRepository extends JpaRepository<TeacherHourlyRate, Long> {

    /**
     * Tarif applicable à une date : période de validité couvrante, active,
     * le plus récent en premier.
     */
    @Query("""
            SELECT r FROM TeacherHourlyRate r
            WHERE r.teacher.id = :teacherId
              AND r.active = true
              AND r.startDate <= :date
              AND (r.endDate IS NULL OR r.endDate >= :date)
            ORDER BY r.startDate DESC, r.id DESC
            """)
    List<TeacherHourlyRate> findApplicable(@Param("teacherId") Long teacherId,
                                           @Param("date") LocalDate date);

    boolean existsByTeacherIdAndStartDate(Long teacherId, LocalDate startDate);

    @Query("""
            SELECT r FROM TeacherHourlyRate r
            WHERE (:teacherId IS NULL OR r.teacher.id = :teacherId)
              AND (:yearId IS NULL OR r.academicYear.id = :yearId)
              AND (:active IS NULL OR r.active = :active)
            ORDER BY r.startDate DESC, r.id DESC
            """)
    List<TeacherHourlyRate> search(@Param("teacherId") Long teacherId,
                                   @Param("yearId") Long yearId,
                                   @Param("active") Boolean active);
}
