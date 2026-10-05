package com.school.repository;

import com.school.entity.LmdDeliberation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository des délibérations LMD.
 */
@Repository
public interface LmdDeliberationRepository extends JpaRepository<LmdDeliberation, Long> {

    Optional<LmdDeliberation> findByStudentIdAndFieldIdAndSemesterAndSession(
            Long studentId, Long fieldId, String semester, int session);

    List<LmdDeliberation> findByFieldIdAndSemesterOrderByAverageDesc(Long fieldId, String semester);

    /**
     * Délibérations d'une filière pour un semestre et une session donnés, en une
     * seule requête (évite le N+1 lors d'un recalcul de délibération).
     */
    List<LmdDeliberation> findByFieldIdAndSemesterAndSession(Long fieldId, String semester, int session);
}
