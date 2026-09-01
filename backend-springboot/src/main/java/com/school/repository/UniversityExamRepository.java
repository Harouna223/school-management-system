package com.school.repository;

import com.school.entity.UniversityExam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface UniversityExamRepository extends JpaRepository<UniversityExam, Long> {

    List<UniversityExam> findByCourseUnitUeFieldIdOrderByDateAsc(Long fieldId);

    List<UniversityExam> findByDateOrderByStartTimeAsc(LocalDate date);

    List<UniversityExam> findBySemesterOrderByDateAsc(String semester);

    boolean existsByCourseUnitIdAndDateAndSession(Long courseUnitId, LocalDate date, int session);
}