package com.school.repository;

import com.school.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository des départements.
 */
@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByFacultyIdOrderByName(Long facultyId);

    boolean existsByNameAndFacultyId(String name, Long facultyId);

    List<Department> findTop8ByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(String name, String code);
}
