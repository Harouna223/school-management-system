package com.school.repository;

import com.school.entity.EcGrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EcGradeRepository extends JpaRepository<EcGrade, Long> {

    List<EcGrade> findByStudentIdAndCourseUnitUeFieldId(Long studentId, Long fieldId);

    List<EcGrade> findByStudentId(Long studentId);

    List<EcGrade> findByStudentIdAndCourseUnitUeId(Long studentId, Long ueId);

    Optional<EcGrade> findByStudentIdAndCourseUnitIdAndSession(Long studentId, Long courseUnitId, int session);

    List<EcGrade> findByStudentIdAndCourseUnitIdOrderBySession(Long studentId, Long courseUnitId);
}