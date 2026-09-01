package com.school.repository;

import com.school.entity.Convocation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConvocationRepository extends JpaRepository<Convocation, Long> {

    Page<Convocation> findByStudentId(Long studentId, Pageable pageable);

    List<Convocation> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<Convocation> findTop8BySubjectContainingIgnoreCaseOrReferenceContainingIgnoreCase(
            String subject, String reference);
}