package com.school.repository;

import com.school.entity.Invoice;
import com.school.enums.InvoiceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByStudentId(Long studentId);

    List<Invoice> findByStatusIn(List<InvoiceStatus> statuses);

    long countByStatus(InvoiceStatus status);

    boolean existsByFeeTypeId(Long feeTypeId);

    boolean existsByInvoiceNo(String invoiceNo);

    /**
     * Verrou pessimiste pour empêcher le double encaissement sur la même facture.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.id = :id")
    Optional<Invoice> findWithLockingById(@Param("id") Long id);

    @Query("""
            SELECT i FROM Invoice i
            WHERE i.status IN (com.school.enums.InvoiceStatus.UNPAID, com.school.enums.InvoiceStatus.PARTIAL)
              AND i.dueDate < :today
            """)
    List<Invoice> findOverdue(LocalDate today);

    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Invoice i")
    BigDecimal sumTotalAmount();

    @Query("SELECT COALESCE(SUM(i.paidAmount), 0) FROM Invoice i")
    BigDecimal sumPaidAmount();

    @Query("""
            SELECT i FROM Invoice i
            WHERE (:studentId IS NULL OR i.student.id = :studentId)
              AND (:status IS NULL OR i.status = :status)
            """)
    Page<Invoice> search(@Param("studentId") Long studentId,
                         @Param("status") InvoiceStatus status,
                         Pageable pageable);

    @Query("""
            SELECT i FROM Invoice i
            WHERE LOWER(i.invoiceNo) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(i.student.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(i.student.lastName) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(i.feeType.name) LIKE LOWER(CONCAT('%', :q, '%'))
            """)
    List<Invoice> findTop8ByKeyword(@Param("q") String q, Pageable pageable);
}
