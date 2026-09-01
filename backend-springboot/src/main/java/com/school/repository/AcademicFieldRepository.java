package com.school.repository;

import com.school.entity.AcademicField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository des filières académiques.
 */
@Repository
public interface AcademicFieldRepository extends JpaRepository<AcademicField, Long> {

    List<AcademicField> findByDepartmentIdOrderByName(Long departmentId);

    boolean existsByName(String name);

    boolean existsByCode(String code);

    List<AcademicField> findTop8ByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(String name, String code);
}
