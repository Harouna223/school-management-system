package com.school.repository;

import com.school.entity.LmdDeliberation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository des délibérations LMD.
 */
@Repository
public interface LmdDeliberationRepository extends JpaRepository<LmdDeliberation, Long> {

    Optional<LmdDeliberation> findByStudentIdAndFieldIdAndSemesterAndSession(
            Long studentId, Long fieldId, String semester, int session);

    List<LmdDeliberation> findByFieldIdAndSemesterOrderByAverageDesc(Long fieldId, String semester);
}
