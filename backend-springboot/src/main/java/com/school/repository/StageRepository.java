package com.school.repository;

import com.school.entity.Stage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StageRepository extends JpaRepository<Stage, Long> {

    /**
     * Chargement avec l'élève et l'encadrant : sans cet {@code EntityGraph},
     * la conversion en DTO déclencherait un N+1 sur chaque stage affiché.
     */
    @EntityGraph(attributePaths = {"student", "supervisor"})
    List<Stage> findAllByOrderByStartDateDesc();

    @EntityGraph(attributePaths = {"student", "supervisor"})
    List<Stage> findByStudentIdOrderByStartDateDesc(Long studentId);

    List<Stage> findByCompanyContainingIgnoreCase(String company);
}