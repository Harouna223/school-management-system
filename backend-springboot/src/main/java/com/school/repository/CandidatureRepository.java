package com.school.repository;

import com.school.entity.Candidature;
import com.school.enums.CandidatureStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CandidatureRepository extends JpaRepository<Candidature, Long> {

    Page<Candidature> findByFieldId(Long fieldId, Pageable pageable);

    Page<Candidature> findByStatus(CandidatureStatus status, Pageable pageable);

    List<Candidature> findTop10ByOrderByCreatedAtDesc();
}