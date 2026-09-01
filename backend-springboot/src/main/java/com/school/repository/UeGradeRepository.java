package com.school.repository;

import com.school.entity.UeGrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository des notes UE.
 */
@Repository
public interface UeGradeRepository extends JpaRepository<UeGrade, Long> {

    Optional<UeGrade> findByStudentIdAndUeIdAndSemester(Long studentId, Long ueId, String semester);

    Optional<UeGrade> findByStudentIdAndUeIdAndSemesterAndSession(Long studentId, Long ueId, String semester, int session);

    List<UeGrade> findByStudentIdAndSemester(Long studentId, String semester);

    List<UeGrade> findByStudentIdAndUeFieldIdAndSemester(Long studentId, Long fieldId, String semester);

    List<UeGrade> findByUeFieldIdAndSemester(Long fieldId, String semester);

    boolean existsByUeId(Long ueId);
}
