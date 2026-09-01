package com.school.repository;

import com.school.entity.Program;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProgramRepository extends JpaRepository<Program, Long> {
    List<Program> findByFieldIdOrderByAcademicYearDesc(Long fieldId);
    boolean existsByCode(String code);
    List<Program> findTop8ByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(String name, String code);
}