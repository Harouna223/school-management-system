package com.school.repository;

import com.school.entity.Borrowing;
import com.school.enums.BorrowingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BorrowingRepository extends JpaRepository<Borrowing, Long> {

    List<Borrowing> findByStudentId(Long studentId);

    long countByStatus(BorrowingStatus status);

    List<Borrowing> findByStatusAndDueDateBefore(BorrowingStatus status, LocalDate date);

    Page<Borrowing> findByStatus(BorrowingStatus status, Pageable pageable);
}
