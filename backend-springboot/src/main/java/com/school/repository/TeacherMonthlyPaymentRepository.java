package com.school.repository;

import com.school.entity.TeacherMonthlyPayment;
import com.school.enums.TeacherPaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TeacherMonthlyPaymentRepository extends JpaRepository<TeacherMonthlyPayment, Long> {

    Optional<TeacherMonthlyPayment> findByTeacherIdAndMonthDate(Long teacherId, LocalDate monthDate);

    /**
     * Même recherche, avec VERROU pessimiste.
     *
     * <p>Sans lui, deux paiements simultanés du même mois lisaient tous les deux le
     * même « reste à payer », passaient le contrôle et créaient chacun une
     * transaction + une dépense : surpaiement réel et comptabilité incohérente.
     * L'encaissement d'une facture élève utilisait déjà ce verrou
     * ({@code InvoiceRepository.findWithLockingById}).</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM TeacherMonthlyPayment p WHERE p.teacher.id = :teacherId AND p.monthDate = :monthDate")
    Optional<TeacherMonthlyPayment> findWithLockingByTeacherIdAndMonthDate(
            @Param("teacherId") Long teacherId, @Param("monthDate") LocalDate monthDate);

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


    /**
     * Garde anti-cascade : compte les enregistrements qui seraient detruits
     * silencieusement avec le parent supprime.
     */
    long countByTeacherId(Long teacherId);
}
