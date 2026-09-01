package com.school.repository;

import com.school.entity.CourseUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseUnitRepository extends JpaRepository<CourseUnit, Long> {
    List<CourseUnit> findByUeIdOrderByCode(Long ueId);
    boolean existsByUeIdAndCode(Long ueId, String code);
    List<CourseUnit> findTop8ByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(String name, String code);
}