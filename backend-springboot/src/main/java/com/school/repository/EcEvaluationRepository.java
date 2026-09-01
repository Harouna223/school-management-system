package com.school.repository;

import com.school.entity.EcEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EcEvaluationRepository extends JpaRepository<EcEvaluation, Long> {
    List<EcEvaluation> findByEcIdOrderBySessionAscEvaluationTypeAsc(Long ecId);
    List<EcEvaluation> findByEcIdAndStudentIdOrderBySessionAsc(Long ecId, Long studentId);
    Optional<EcEvaluation> findByEcIdAndStudentIdAndEvaluationTypeAndSession(
            Long ecId, Long studentId, com.school.enums.EvaluationType evaluationType, int session);
}