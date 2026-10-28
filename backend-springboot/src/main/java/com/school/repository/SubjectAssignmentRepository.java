package com.school.repository;

import com.school.entity.SubjectAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SubjectAssignmentRepository extends JpaRepository<SubjectAssignment, Long> {

    List<SubjectAssignment> findByTeacherId(Long teacherId);

    List<SubjectAssignment> findBySchoolClassId(Long classId);

    long countBySchoolClassId(Long classId);

    boolean existsByTeacherIdAndSubjectIdAndSchoolClassId(Long teacherId, Long subjectId, Long classId);

    /** Vrai si l'enseignant intervient dans cette classe, quelle que soit la matière. */
    boolean existsByTeacherIdAndSchoolClassId(Long teacherId, Long classId);

    /** Classes où l'enseignant a au moins une affectation (périmètre de données). */
    @Query("SELECT DISTINCT a.schoolClass.id FROM SubjectAssignment a WHERE a.teacher.id = :teacherId")
    List<Long> findClassIdsByTeacherId(@Param("teacherId") Long teacherId);


    /**
     * Garde anti-cascade : compte les enregistrements qui seraient detruits
     * silencieusement avec le parent supprime.
     */
    long countByTeacherId(Long teacherId);
    long countBySubjectId(Long subjectId);
}
