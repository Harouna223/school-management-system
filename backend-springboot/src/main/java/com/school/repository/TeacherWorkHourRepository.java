package com.school.repository;

import com.school.entity.TeacherWorkHour;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TeacherWorkHourRepository extends JpaRepository<TeacherWorkHour, Long> {

    /**
     * Totaux mensuels par professeur : heures et montants sommés depuis les
     * saisies journalières (chaque ligne conservant son propre tarif appliqué).
     */
    interface TeacherMonthTotals {
        Long getTeacherId();

        BigDecimal getTotalHours();

        BigDecimal getTotalAmount();
    }

    @Query("""
            SELECT w.teacher.id AS teacherId,
                   SUM(w.hours) AS totalHours,
                   SUM(w.amount) AS totalAmount
            FROM TeacherWorkHour w
            WHERE w.date >= :from AND w.date <= :to
              AND (:teacherId IS NULL OR w.teacher.id = :teacherId)
            GROUP BY w.teacher.id
            """)
    List<TeacherMonthTotals> sumByMonth(@Param("from") LocalDate from,
                                        @Param("to") LocalDate to,
                                        @Param("teacherId") Long teacherId);

    /** Dernière saisie d'un professeur dans le mois (pour le tarif de référence). */
    Optional<TeacherWorkHour> findTop1ByTeacherIdAndDateBetweenOrderByIdDesc(
            Long teacherId, LocalDate from, LocalDate to);

    Optional<TeacherWorkHour> findByTeacherIdAndDateAndSubjectIdAndSchoolClassId(
            Long teacherId, LocalDate date, Long subjectId, Long classId);

    /**
     * Détection de doublon NULL-safe.
     *
     * <p>La méthode dérivée ci-dessus ne sait pas exprimer « matière absente » :
     * appelée avec une sentinelle {@code -1L}, elle produisait
     * {@code subject_id = -1 AND class_id = -1}, qui ne correspond jamais à
     * {@code NULL}. Or matière et classe sont facultatives et MySQL n'applique pas
     * l'unicité sur des NULL ({@code uk_twh_teacher_date_subject_class}) : deux
     * saisies de 8 h le même jour sans matière ni classe étaient donc acceptées et
     * le salaire du mois doublait. Ici, c'est « IS NULL » qui est testé.</p>
     */
    @Query("""
            SELECT w FROM TeacherWorkHour w
            WHERE w.teacher.id = :teacherId
              AND w.date = :date
              AND (:subjectId IS NULL AND w.subject IS NULL OR w.subject.id = :subjectId)
              AND (:classId IS NULL AND w.schoolClass IS NULL OR w.schoolClass.id = :classId)
            """)
    List<TeacherWorkHour> findDuplicates(@Param("teacherId") Long teacherId,
                                         @Param("date") LocalDate date,
                                         @Param("subjectId") Long subjectId,
                                         @Param("classId") Long classId);

    @Query("""
            SELECT w FROM TeacherWorkHour w
            WHERE (:teacherId IS NULL OR w.teacher.id = :teacherId)
              AND (:subjectId IS NULL OR w.subject.id = :subjectId)
              AND (:classId IS NULL OR w.schoolClass.id = :classId)
              AND (:from IS NULL OR w.date >= :from)
              AND (:to IS NULL OR w.date <= :to)
              AND (:yearId IS NULL OR w.academicYear.id = :yearId)
            """)
    Page<TeacherWorkHour> search(@Param("teacherId") Long teacherId,
                                 @Param("subjectId") Long subjectId,
                                 @Param("classId") Long classId,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to,
                                 @Param("yearId") Long yearId,
                                 Pageable pageable);

    List<TeacherWorkHour> findByTeacherIdAndDateOrderBySubjectAsc(Long teacherId, LocalDate date);

    /** Somme des heures déjà saisies pour un professeur un jour donné (contrôle des heures excessives). */
    @Query("SELECT COALESCE(SUM(w.hours), 0) FROM TeacherWorkHour w WHERE w.teacher.id = :teacherId AND w.date = :date")
    BigDecimal sumHoursOfTeacherOnDate(@Param("teacherId") Long teacherId, @Param("date") LocalDate date);

    /**
     * Même somme, en EXCLUANT une saisie : indispensable à la correction, sinon
     * l'ancienne valeur de la ligne modifiée était comptée deux fois.
     */
    @Query("""
            SELECT COALESCE(SUM(w.hours), 0) FROM TeacherWorkHour w
            WHERE w.teacher.id = :teacherId AND w.date = :date AND w.id <> :excludeId
            """)
    BigDecimal sumHoursOfTeacherOnDateExcluding(@Param("teacherId") Long teacherId,
                                                @Param("date") LocalDate date,
                                                @Param("excludeId") Long excludeId);
}
