package com.school.repository;

import com.school.entity.Bulletin;
import com.school.enums.Term;
import org.springframework.data.jpa.repository.JpaRepository;

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

    List<Bulletin> findByStudentIdOrderByAcademicYearDescTermDesc(Long studentId);
}
