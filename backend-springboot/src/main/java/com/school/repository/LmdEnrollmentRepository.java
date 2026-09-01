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

    List<LmdEnrollment> findByStudentIdOrderByIdDesc(Long studentId);
}
