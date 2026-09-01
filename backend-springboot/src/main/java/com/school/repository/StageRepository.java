package com.school.repository;

import com.school.entity.Stage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StageRepository extends JpaRepository<Stage, Long> {

    List<Stage> findByStudentIdOrderByStartDateDesc(Long studentId);

    List<Stage> findByCompanyContainingIgnoreCase(String company);
}