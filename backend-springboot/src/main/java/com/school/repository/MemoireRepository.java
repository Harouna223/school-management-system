package com.school.repository;

import com.school.entity.Memoire;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemoireRepository extends JpaRepository<Memoire, Long> {

    /**
     * Chargement avec l'élève et le directeur : sans cet {@code EntityGraph},
     * la conversion en DTO déclencherait un N+1 sur chaque mémoire affiché.
     */
    @EntityGraph(attributePaths = {"student", "director"})
    List<Memoire> findAllByOrderByDefenseDateDesc();

    @EntityGraph(attributePaths = {"student", "director"})
    List<Memoire> findByStudentIdOrderByDefenseDateDesc(Long studentId);

    List<Memoire> findBySubjectContainingIgnoreCase(String subject);
}