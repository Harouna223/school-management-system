package com.school.repository;

import com.school.entity.UeEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UeEnrollmentRepository extends JpaRepository<UeEnrollment, Long> {
    List<UeEnrollment> findByStudentIdAndUeFieldId(Long studentId, Long fieldId);
    List<UeEnrollment> findByStudentId(Long studentId);
    boolean existsByStudentIdAndUeId(Long studentId, Long ueId);

    /**
     * Inscriptions UE de plusieurs UE en une seule requête : évite le N+1
     * lors du calcul des crédits (UE optionnelles choisies).
     */
    List<UeEnrollment> findByUeIdIn(List<Long> ueIds);
}