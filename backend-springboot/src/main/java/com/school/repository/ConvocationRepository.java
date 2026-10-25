package com.school.repository;

import com.school.entity.Convocation;
import com.school.enums.ConvocationTarget;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConvocationRepository extends JpaRepository<Convocation, Long> {

    Page<Convocation> findByStudentId(Long studentId, Pageable pageable);

    Page<Convocation> findByParentId(Long parentId, Pageable pageable);

    Page<Convocation> findByTeacherId(Long teacherId, Pageable pageable);

    /** Filtre par cible (onglets « Élèves / Parents / Enseignants »). */
    Page<Convocation> findByTarget(ConvocationTarget target, Pageable pageable);

    List<Convocation> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<Convocation> findTop8BySubjectContainingIgnoreCaseOrReferenceContainingIgnoreCase(
            String subject, String reference);
}