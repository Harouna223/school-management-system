package com.school.repository;

import com.school.entity.Bulletin;
import com.school.enums.Term;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BulletinRepository extends JpaRepository<Bulletin, Long> {

    Optional<Bulletin> findByStudentIdAndTermAndAcademicYear(Long studentId,
                                                             Term term,
                                                             String academicYear);

    List<Bulletin> findByAcademicYear(String academicYear);

    List<Bulletin> findByStudentId(Long studentId);

    List<Bulletin> findByStudentSchoolClassIdAndTermAndAcademicYear(Long classId,
                                                                    Term term,
                                                                    String academicYear);

    /**
     * Année scolaire la plus récente pour laquelle des bulletins existent pour
     * cette classe et ce trimestre. Sert de repli aux exports PDF : sans lui,
     * l'ancien libellé d'année (calculé depuis l'année civile) ne correspondait à
     * aucun bulletin et l'export répondait « Aucun bulletin trouvé ».
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT MAX(b.academicYear) FROM Bulletin b
            WHERE b.student.schoolClass.id = :classId AND b.term = :term
            """)
    String findLatestAcademicYear(@Param("classId") Long classId, @Param("term") Term term);

    List<Bulletin> findByStudentIdOrderByAcademicYearDescTermDesc(Long studentId);
}
