package com.school.service;

import com.school.dto.request.CandidatureRequest;
import com.school.dto.response.CandidatureResponse;
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

/**
 * Gestion des candidatures d'admission universitaire.
 */
@Service
@RequiredArgsConstructor
public class CandidatureService {

    /** Préfixe du compteur persisté des références de candidature. */
    private static final String REFERENCE_PREFIX = "CAND-";

    private final CandidatureRepository candidatureRepository;
    private final NumberSequenceService numberSequenceService;
    private final AuditService auditService;

    @Transactional
    public CandidatureResponse create(CandidatureRequest request, HttpServletRequest httpRequest) {
        Candidature candidature = Candidature.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .birthDate(request.getBirthDate())
                .email(request.getEmail())
                .phone(request.getPhone())
                .fieldName(request.getFieldName().trim())
                .level(request.getLevel())
                .notes(request.getNotes())
                .status(CandidatureStatus.EN_ATTENTE)
                .build();
        // Compteur persisté et verrouillé : un compteur en mémoire repartait de
        // zéro à chaque redémarrage et pouvait réémettre la même référence.
        candidature.setReference(REFERENCE_PREFIX
                + String.format("%06d", numberSequenceService.next(REFERENCE_PREFIX)));
        Candidature saved = candidatureRepository.save(candidature);
        auditService.log("CREATE", "Candidature", saved.getId(),
                "Candidature " + saved.getReference() + " " + saved.getFirstName() + " " + saved.getLastName(), httpRequest);
        return CandidatureResponse.from(saved);
    }

    @Transactional
    public CandidatureResponse updateStatus(Long id, CandidatureStatus status, HttpServletRequest httpRequest) {
        Candidature candidature = candidatureRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Candidature", id));
        candidature.setStatus(status);
        Candidature saved = candidatureRepository.save(candidature);
        auditService.log("UPDATE", "Candidature", id,
                "Statut candidature " + saved.getReference() + " → " + status, httpRequest);
        return CandidatureResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<CandidatureResponse> list(String fieldName, CandidatureStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Candidature> result;
        if (status != null) {
            result = candidatureRepository.findByStatus(status, pageable);
        } else if (fieldName != null && !fieldName.isBlank()) {
            result = candidatureRepository.findByFieldNameContainingIgnoreCase(fieldName.trim(), pageable);
        } else {
            result = candidatureRepository.findAll(pageable);
        }
        return result.map(CandidatureResponse::from);
    }

    @Transactional
    public void delete(Long id, HttpServletRequest httpRequest) {
        Candidature candidature = candidatureRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Candidature", id));
        auditService.log("DELETE", "Candidature", id, "Suppression candidature " + candidature.getReference(), httpRequest);
        candidatureRepository.delete(candidature);
    }
}
