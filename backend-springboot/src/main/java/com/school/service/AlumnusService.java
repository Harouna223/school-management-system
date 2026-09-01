package com.school.service;

import com.school.entity.Alumnus;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.AlumnusRepository;
import com.school.repository.AcademicFieldRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestion des diplômés (alumni).
 */
@Service
@RequiredArgsConstructor
public class AlumnusService {

    private final AlumnusRepository alumnusRepository;
    private final AcademicFieldRepository fieldRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<Alumnus> listAll() {
        return alumnusRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Alumnus> listByField(Long fieldId) {
        return fieldId != null ? alumnusRepository.findByFieldId(fieldId) : alumnusRepository.findAll();
    }

    @Transactional
    public Alumnus create(Alumnus alumnus, HttpServletRequest httpRequest) {
        if (alumnus.getFirstName() == null || alumnus.getLastName() == null) {
            throw new BusinessException("Le prénom et le nom sont obligatoires");
        }
        if (alumnus.getField() != null && alumnus.getField().getId() != null) {
            fieldRepository.findById(alumnus.getField().getId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Filière", alumnus.getField().getId()));
        }
        Alumnus saved = alumnusRepository.save(alumnus);
        auditService.log("CREATE", "Alumnus", saved.getId(),
                "Alumni " + saved.getFirstName() + " " + saved.getLastName(), httpRequest);
        return saved;
    }

    @Transactional
    public void delete(Long id, HttpServletRequest httpRequest) {
        Alumnus alumnus = alumnusRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Alumni", id));
        auditService.log("DELETE", "Alumnus", id, "Suppression alumni " + alumnus.getFirstName(), httpRequest);
        alumnusRepository.delete(alumnus);
    }
}