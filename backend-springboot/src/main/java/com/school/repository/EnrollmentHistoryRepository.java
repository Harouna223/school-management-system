package com.school.repository;

import com.school.entity.EnrollmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EnrollmentHistoryRepository extends JpaRepository<EnrollmentHistory, Long> {
    List<EnrollmentHistory> findByStudentIdOrderByCreatedAtDesc(Long studentId);
}