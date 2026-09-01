package com.school.repository;

import com.school.entity.Payment;
import com.school.enums.PaymentMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByStudentId(Long studentId);

    List<Payment> findByInvoiceId(Long invoiceId);

    boolean existsByReceiptNo(String receiptNo);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.paymentDate >= :from AND p.paymentDate < :to")
    BigDecimal sumBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.paymentDate >= :from")
    BigDecimal sumFrom(@Param("from") LocalDateTime from);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p")
    BigDecimal sumTotal();

    long countByPaymentDateBetween(LocalDateTime from, LocalDateTime to);

    @Query("""
            SELECT p FROM Payment p
            WHERE (:studentId IS NULL OR p.student.id = :studentId)
              AND (:method IS NULL OR p.method = :method)
              AND (:from IS NULL OR p.paymentDate >= :from)
              AND (:to IS NULL OR p.paymentDate <= :to)
            """)
    Page<Payment> search(@Param("studentId") Long studentId,
                         @Param("method") PaymentMethod method,
                         @Param("from") LocalDateTime from,
                         @Param("to") LocalDateTime to,
                         Pageable pageable);
}
