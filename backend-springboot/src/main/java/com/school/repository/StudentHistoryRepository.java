package com.school.repository;

import com.school.entity.StudentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentHistoryRepository extends JpaRepository<StudentHistory, Long> {

    List<StudentHistory> findByStudentIdOrderByCreatedAtDesc(Long studentId);
}