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

    /**
     * Notes d'une évaluation pour une année scolaire donnée. L'écran de saisie
     * listait toutes les années confondues : on pouvait donc renoter une
     * évaluation de l'année précédente et fausser l'année en cours.
     */
    @Query("""
            SELECT g FROM Grade g
            WHERE g.exam.id = :examId AND g.exam.academicYear = :academicYear
            """)
    List<Grade> findByExamIdAndYear(@Param("examId") Long examId,
                                    @Param("academicYear") String academicYear);

    /**
     * Année scolaire d'une évaluation donnée (pour rester cohérent avec la note saisie).
     */
    @Query("SELECT g.exam.academicYear FROM Grade g WHERE g.exam.id = :examId")
    List<String> academicYearsOfExam(@Param("examId") Long examId);

    List<Grade> findByStudentId(Long studentId);

    long countByExamId(Long examId);

    @Query("SELECT AVG(g.value) FROM Grade g WHERE g.exam.id = :examId")
    BigDecimal findAverageByExamId(@Param("examId") Long examId);

    @Query("SELECT g FROM Grade g WHERE g.student.id = :studentId AND g.exam.term = :term")
    List<Grade> findByStudentIdAndTerm(@Param("studentId") Long studentId,
                                       @Param("term") com.school.enums.Term term);

    /**
     * Notes d'un élève pour un trimestre D'UNE ANNÉE SCOLAIRE donnée.
     *
     * <p>Sans ce filtre, {@code Term} (T1/T2/T3) était la seule clé de période :
     * les notes de l'année précédente étaient comptées dans la moyenne de l'année
     * en cours (moyennes, rangs, mentions, décisions de délibération faussés).</p>
     */
    @Query("""
            SELECT g FROM Grade g
            WHERE g.student.id = :studentId
              AND g.exam.term = :term
              AND g.exam.academicYear = :academicYear
            """)
    List<Grade> findByStudentIdAndTermAndYear(@Param("studentId") Long studentId,
                                              @Param("term") com.school.enums.Term term,
                                              @Param("academicYear") String academicYear);

    @Query("""
            SELECT g FROM Grade g
            WHERE g.exam.schoolClass.id = :classId AND g.exam.term = :term
            """)
    List<Grade> findByClassIdAndTerm(@Param("classId") Long classId,
                                     @Param("term") com.school.enums.Term term);

    @Query("""
            SELECT g FROM Grade g
            WHERE g.student.id = :studentId
              AND g.exam.academicYear = :academicYear
            """)
    List<Grade> findByStudentIdAndYear(@Param("studentId") Long studentId,
                                       @Param("academicYear") String academicYear);

    @Query("""
            SELECT g FROM Grade g
            WHERE g.exam.schoolClass.id = :classId
              AND g.exam.term = :term
              AND g.exam.academicYear = :academicYear
            """)
    List<Grade> findByClassIdAndTermAndYear(@Param("classId") Long classId,
                                            @Param("term") com.school.enums.Term term,
                                            @Param("academicYear") String academicYear);
}
