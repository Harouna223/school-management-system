package com.school.repository;

import com.school.entity.Borrowing;
import com.school.enums.BorrowingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BorrowingRepository extends JpaRepository<Borrowing, Long> {

    List<Borrowing> findByStudentId(Long studentId);

    long countByStatus(BorrowingStatus status);

    boolean existsByBookId(Long bookId);

    List<Borrowing> findByStatusAndDueDateBefore(BorrowingStatus status, LocalDate date);

    Page<Borrowing> findByStatus(BorrowingStatus status, Pageable pageable);

    /**
     * Verrou pessimiste : deux retours simultanés du même emprunt passaient tous
     * les deux le contrôle de statut et incrémentaient le stock deux fois.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Borrowing b WHERE b.id = :id")
    Optional<Borrowing> findWithLockingById(@Param("id") Long id);
}
