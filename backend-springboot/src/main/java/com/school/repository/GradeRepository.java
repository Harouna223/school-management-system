package com.school.repository;

import com.school.entity.Grade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface GradeRepository extends JpaRepository<Grade, Long> {

    Optional<Grade> findByStudentIdAndExamId(Long studentId, Long examId);

    List<Grade> findByExamId(Long examId);

    List<Grade> findByStudentId(Long studentId);

    long countByExamId(Long examId);

    @Query("SELECT AVG(g.value) FROM Grade g WHERE g.exam.id = :examId")
    BigDecimal findAverageByExamId(@Param("examId") Long examId);

    @Query("SELECT g FROM Grade g WHERE g.student.id = :studentId AND g.exam.term = :term")
    List<Grade> findByStudentIdAndTerm(@Param("studentId") Long studentId,
                                       @Param("term") com.school.enums.Term term);

    @Query("""
            SELECT g FROM Grade g
            WHERE g.exam.schoolClass.id = :classId AND g.exam.term = :term
            """)
    List<Grade> findByClassIdAndTerm(@Param("classId") Long classId,
                                     @Param("term") com.school.enums.Term term);
}
