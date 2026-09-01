package com.school.service;

import com.school.dto.request.AcademicYearRequest;
import com.school.entity.AcademicYear;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.AcademicYearRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestion des années scolaires.
 */
@Service
@RequiredArgsConstructor
public class AcademicYearService {

    private final AcademicYearRepository academicYearRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<AcademicYear> findAll() {
        return academicYearRepository.findAll(Sort.by(Sort.Direction.DESC, "startDate"));
    }

    @Transactional(readOnly = true)
    public AcademicYear getById(Long id) {
        return findById(id);
    }

    @Transactional
    public AcademicYear create(AcademicYearRequest request, HttpServletRequest httpRequest) {
        if (academicYearRepository.existsByLabel(request.getLabel())) {
            throw new BusinessException("Une année scolaire porte déjà ce libellé : " + request.getLabel());
        }
        AcademicYear year = AcademicYear.builder()
                .label(request.getLabel())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .current(Boolean.TRUE.equals(request.getIsCurrent()))
                .build();
        if (year.isCurrent()) {
            clearCurrentFlag();
        }
        AcademicYear saved = academicYearRepository.save(year);
        auditService.log("CREATE", "AcademicYear", saved.getId(),
                "Création année scolaire " + saved.getLabel(), httpRequest);
        return saved;
    }

    @Transactional
    public AcademicYear update(Long id, AcademicYearRequest request, HttpServletRequest httpRequest) {
        AcademicYear year = findById(id);
        boolean labelConflict = !year.getLabel().equals(request.getLabel())
                && academicYearRepository.existsByLabel(request.getLabel());
        if (labelConflict) {
            throw new BusinessException("Une année scolaire porte déjà ce libellé : " + request.getLabel());
        }
        year.setLabel(request.getLabel());
        year.setStartDate(request.getStartDate());
        year.setEndDate(request.getEndDate());
        if (Boolean.TRUE.equals(request.getIsCurrent()) && !year.isCurrent()) {
            clearCurrentFlag();
            year.setCurrent(true);
        } else if (Boolean.FALSE.equals(request.getIsCurrent())) {
            year.setCurrent(false);
        }
        auditService.log("UPDATE", "AcademicYear", id,
                "Modification année scolaire " + year.getLabel(), httpRequest);
        return academicYearRepository.save(year);
    }

    /**
     * Définit l'année courante (une seule à la fois).
     */
    @Transactional
    public AcademicYear setCurrent(Long id, HttpServletRequest httpRequest) {
        AcademicYear year = findById(id);
        clearCurrentFlag();
        year.setCurrent(true);
        auditService.log("SET_CURRENT", "AcademicYear", id,
                "Année scolaire active : " + year.getLabel(), httpRequest);
        return academicYearRepository.save(year);
    }

    @Transactional
    public void delete(Long id, HttpServletRequest httpRequest) {
        AcademicYear year = findById(id);
        if (year.isCurrent()) {
            throw new BusinessException("Impossible de supprimer l'année scolaire active. "
                    + "Désignez d'abord une autre année active.");
        }
        auditService.log("DELETE", "AcademicYear", id,
                "Suppression année scolaire " + year.getLabel(), httpRequest);
        academicYearRepository.delete(year);
    }

    private void clearCurrentFlag() {
        academicYearRepository.findByCurrentTrue()
                .ifPresent(previous -> {
                    previous.setCurrent(false);
                    academicYearRepository.save(previous);
                });
    }

    private AcademicYear findById(Long id) {
        return academicYearRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Année scolaire", id));
    }
}
