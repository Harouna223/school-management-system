package com.school.repository;

import com.school.entity.Exam;
import com.school.enums.ExamStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExamRepository extends JpaRepository<Exam, Long> {

    List<Exam> findBySchoolClassId(Long classId);

    List<Exam> findBySubjectId(Long subjectId);

    List<Exam> findByStatus(ExamStatus status);

    @Query("""
            SELECT e FROM Exam e
            WHERE (:search IS NULL OR :search = '' OR LOWER(e.name) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:classId IS NULL OR e.schoolClass.id = :classId)
              AND (:subjectId IS NULL OR e.subject.id = :subjectId)
              AND (:term IS NULL OR e.term = :term)
              AND (:status IS NULL OR e.status = :status)
            """)
    Page<Exam> search(@Param("search") String search,
                      @Param("classId") Long classId,
                      @Param("subjectId") Long subjectId,
                      @Param("term") com.school.enums.Term term,
                      @Param("status") ExamStatus status,
                      Pageable pageable);

    @Query("""
            SELECT e FROM Exam e
            WHERE e.examDate IS NOT NULL
              AND e.examDate >= :from AND e.examDate <= :to
              AND e.status <> com.school.enums.ExamStatus.DELIBERATED
            """)
    List<Exam> findByExamDateBetween(@Param("from") java.time.LocalDate from,
                                     @Param("to") java.time.LocalDate to);
}
