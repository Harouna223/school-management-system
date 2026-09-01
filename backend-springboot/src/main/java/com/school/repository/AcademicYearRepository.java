package com.school.repository;

import com.school.entity.AcademicYear;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository des années scolaires.
 */
@Repository
public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {

    boolean existsByLabel(String label);

    Optional<AcademicYear> findByCurrentTrue();
}
