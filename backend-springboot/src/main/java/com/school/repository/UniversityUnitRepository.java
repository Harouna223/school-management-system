package com.school.repository;

import com.school.entity.UniversityUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository des unités d'enseignement (UE).
 */
@Repository
public interface UniversityUnitRepository extends JpaRepository<UniversityUnit, Long> {

    List<UniversityUnit> findByFieldIdOrderBySemester(Long fieldId);

    List<UniversityUnit> findByFieldIdAndSemesterOrderByCode(Long fieldId, String semester);

    boolean existsByCode(String code);

    List<UniversityUnit> findTop8ByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(String name, String code);
}
