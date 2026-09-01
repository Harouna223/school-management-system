package com.school.repository;

import com.school.entity.UeEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UeEnrollmentRepository extends JpaRepository<UeEnrollment, Long> {
    List<UeEnrollment> findByStudentIdAndUeFieldId(Long studentId, Long fieldId);
    List<UeEnrollment> findByStudentId(Long studentId);
    boolean existsByStudentIdAndUeId(Long studentId, Long ueId);
}