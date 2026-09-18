package com.school.repository;

import com.school.entity.TeacherPaymentTransaction;
import com.school.enums.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TeacherPaymentTransactionRepository extends JpaRepository<TeacherPaymentTransaction, Long> {

    List<TeacherPaymentTransaction> findByMonthlyPaymentIdOrderByPaymentDateDesc(Long monthlyPaymentId);

    Optional<TeacherPaymentTransaction> findByReceiptNo(String receiptNo);

    @Query("""
            SELECT t FROM TeacherPaymentTransaction t
            WHERE (:teacherId IS NULL OR t.monthlyPayment.teacher.id = :teacherId)
              AND (:monthDate IS NULL OR t.monthlyPayment.monthDate = :monthDate)
              AND (:method IS NULL OR t.method = :method)
              AND (:from IS NULL OR t.paymentDate >= :from)
              AND (:to IS NULL OR t.paymentDate < :to)
            ORDER BY t.paymentDate DESC, t.id DESC
            """)
    List<TeacherPaymentTransaction> search(@Param("teacherId") Long teacherId,
                                           @Param("monthDate") LocalDate monthDate,
                                           @Param("method") PaymentMethod method,
                                           @Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM TeacherPaymentTransaction t WHERE t.monthlyPayment.id = :monthlyPaymentId")
    BigDecimal sumAmountOf(@Param("monthlyPaymentId") Long monthlyPaymentId);
}
