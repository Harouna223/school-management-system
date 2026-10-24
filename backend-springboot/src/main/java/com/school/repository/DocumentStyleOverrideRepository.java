package com.school.repository;

import com.school.entity.DocumentStyleOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentStyleOverrideRepository extends JpaRepository<DocumentStyleOverride, Long> {

    Optional<DocumentStyleOverride> findByDocumentType(String documentType);

    void deleteByDocumentType(String documentType);
}
