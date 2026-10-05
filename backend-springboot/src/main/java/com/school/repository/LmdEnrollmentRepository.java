package com.school.repository;

import com.school.entity.LmdEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository des inscriptions LMD.
 */
@Repository
public interface LmdEnrollmentRepository extends JpaRepository<LmdEnrollment, Long> {

    Optional<LmdEnrollment> findByStudentIdAndFieldId(Long studentId, Long fieldId);

    List<LmdEnrollment> findByFieldIdAndActiveTrueOrderById(Long fieldId);

    /**
     * Inscriptions actives d'une filière dont le semestre courant correspond au
     * semestre délibéré : un étudiant avançant en S4 ne doit pas être délibéré
     * pour S1 (D2).
     */
    List<LmdEnrollment> findByFieldIdAndActiveTrueAndCurrentSemesterOrderById(
            Long fieldId, String currentSemester);

    List<LmdEnrollment> findByStudentIdOrderByIdDesc(Long studentId);
}
