package com.school.repository;

import com.school.entity.Semester;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SemesterRepository extends JpaRepository<Semester, Long> {
    List<Semester> findByFieldIdOrderByOrderIndex(Long fieldId);
    boolean existsByFieldIdAndCode(Long fieldId, String code);
}