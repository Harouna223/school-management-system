package com.school.repository;

import com.school.entity.UniversityGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UniversityGroupRepository extends JpaRepository<UniversityGroup, Long> {
    List<UniversityGroup> findByFieldId(Long fieldId);
    boolean existsByFieldIdAndCode(Long fieldId, String code);
}