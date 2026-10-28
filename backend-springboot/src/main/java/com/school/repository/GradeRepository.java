package com.school.repository;

import com.school.entity.Grade;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Accès aux notes.
 *
 * <p>Les réponses de note exposent l'élève, l'évaluation et sa matière : ces
 * associations sont chargées en une seule requête par {@code @EntityGraph}
 * plutôt qu'une par note (N+1).</p>
 */
public interface GradeRepository extends JpaRepository<Grade, Long> {

    @EntityGraph(attributePaths = {"student", "exam", "exam.subject"})
    Optional<Grade> findByStudentIdAndExamId(Long studentId, Long examId);

    @EntityGraph(attributePaths = {"student", "exam", "exam.subject"})
    List<Grade> findByExamId(Long examId);

    /**
     * Notes d'une évaluation pour une année scolaire donnée. L'écran de saisie
     * listait toutes les années confondues : on pouvait donc renoter une
     * évaluation de l'année précédente et fausser l'année en cours.
     */
    @EntityGraph(attributePaths = {"student", "exam", "exam.subject"})
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

    @EntityGraph(attributePaths = {"student", "exam", "exam.subject"})
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
    @EntityGraph(attributePaths = {"exam", "exam.subject"})
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

    @EntityGraph(attributePaths = {"student", "exam", "exam.subject"})
    @Query("""
            SELECT g FROM Grade g
            WHERE g.student.id = :studentId
              AND g.exam.academicYear = :academicYear
            """)
    List<Grade> findByStudentIdAndYear(@Param("studentId") Long studentId,
                                       @Param("academicYear") String academicYear);

    @EntityGraph(attributePaths = {"exam", "exam.subject"})
    @Query("""
            SELECT g FROM Grade g
            WHERE g.exam.schoolClass.id = :classId
              AND g.exam.term = :term
              AND g.exam.academicYear = :academicYear
            """)
    List<Grade> findByClassIdAndTermAndYear(@Param("classId") Long classId,
                                            @Param("term") com.school.enums.Term term,
                                            @Param("academicYear") String academicYear);

    /**
     * Notes d'un ensemble d'élèves pour un trimestre et une année : permet de
     * calculer tout un classement en une requête au lieu d'une par élève.
     */
    @EntityGraph(attributePaths = {"exam", "exam.subject"})
    @Query("""
            SELECT g FROM Grade g
            WHERE g.student.id IN :studentIds
              AND g.exam.term = :term
              AND g.exam.academicYear = :academicYear
            """)
    List<Grade> findByStudentIdInAndTermAndYear(@Param("studentIds") java.util.Collection<Long> studentIds,
                                                @Param("term") com.school.enums.Term term,
                                                @Param("academicYear") String academicYear);
}
