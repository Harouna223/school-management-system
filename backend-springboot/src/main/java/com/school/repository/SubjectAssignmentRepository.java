package com.school.repository;

import com.school.entity.SubjectAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubjectAssignmentRepository extends JpaRepository<SubjectAssignment, Long> {

    List<SubjectAssignment> findByTeacherId(Long teacherId);

    List<SubjectAssignment> findBySchoolClassId(Long classId);

    long countBySchoolClassId(Long classId);

    boolean existsByTeacherIdAndSubjectIdAndSchoolClassId(Long teacherId, Long subjectId, Long classId);


    /**
     * Garde anti-cascade : compte les enregistrements qui seraient detruits
     * silencieusement avec le parent supprime.
     */
    long countByTeacherId(Long teacherId);
    long countBySubjectId(Long subjectId);
}
