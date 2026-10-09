package com.school.service;

import com.school.entity.Alumnus;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.AlumnusRepository;
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
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<Alumnus> listAll() {
        return alumnusRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Alumnus> listByField(String fieldName) {
        return (fieldName != null && !fieldName.isBlank())
                ? alumnusRepository.findByFieldNameContainingIgnoreCase(fieldName.trim())
                : alumnusRepository.findAll();
    }

    @Transactional
    public Alumnus create(Alumnus alumnus, HttpServletRequest httpRequest) {
        if (alumnus.getFirstName() == null || alumnus.getLastName() == null) {
            throw new BusinessException("Le prénom et le nom sont obligatoires");
        }
        if (alumnus.getFieldName() != null) {
            alumnus.setFieldName(alumnus.getFieldName().trim());
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