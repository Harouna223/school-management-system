package com.school.service;

import com.school.entity.Candidature;
import com.school.enums.CandidatureStatus;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.CandidatureRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Gestion des candidatures d'admission universitaire.
 */
@Service
@RequiredArgsConstructor
public class CandidatureService {

    private final CandidatureRepository candidatureRepository;
    private final AuditService auditService;

    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis() % 100000);

    @Transactional
    public Candidature create(Candidature candidature, HttpServletRequest httpRequest) {
        if (candidature.getFieldName() == null || candidature.getFieldName().isBlank()) {
            throw new com.school.exception.BusinessException("La filière est obligatoire");
        }
        candidature.setFieldName(candidature.getFieldName().trim());
        candidature.setReference("CAND-" + String.format("%06d", SEQUENCE.incrementAndGet()));
        if (candidature.getStatus() == null) {
            candidature.setStatus(CandidatureStatus.EN_ATTENTE);
        }
        Candidature saved = candidatureRepository.save(candidature);
        auditService.log("CREATE", "Candidature", saved.getId(),
                "Candidature " + saved.getReference() + " " + saved.getFirstName() + " " + saved.getLastName(), httpRequest);
        return saved;
    }

    @Transactional
    public Candidature updateStatus(Long id, CandidatureStatus status, HttpServletRequest httpRequest) {
        Candidature candidature = candidatureRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Candidature", id));
        candidature.setStatus(status);
        Candidature saved = candidatureRepository.save(candidature);
        auditService.log("UPDATE", "Candidature", id,
                "Statut candidature " + saved.getReference() + " → " + status, httpRequest);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Candidature> list(String fieldName, CandidatureStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (status != null) return candidatureRepository.findByStatus(status, pageable);
        if (fieldName != null && !fieldName.isBlank()) {
            return candidatureRepository.findByFieldNameContainingIgnoreCase(fieldName.trim(), pageable);
        }
        return candidatureRepository.findAll(pageable);
    }

    @Transactional
    public void delete(Long id, HttpServletRequest httpRequest) {
        Candidature candidature = candidatureRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Candidature", id));
        auditService.log("DELETE", "Candidature", id, "Suppression candidature " + candidature.getReference(), httpRequest);
        candidatureRepository.delete(candidature);
    }
}