package com.school.repository;

import com.school.entity.AcademicRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AcademicRuleRepository extends JpaRepository<AcademicRule, Long> {
    Optional<AcademicRule> findByCycleAndRuleKey(String cycle, String ruleKey);
    List<AcademicRule> findByCycleOrCycleIsNull(String cycle);
}