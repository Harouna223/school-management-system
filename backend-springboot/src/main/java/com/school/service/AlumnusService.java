package com.school.service;

import com.school.dto.request.AlumnusRequest;
import com.school.dto.response.AlumnusResponse;
import com.school.entity.Alumnus;
import com.school.entity.Student;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.AlumnusRepository;
import com.school.repository.StudentRepository;
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
    private final StudentRepository studentRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<AlumnusResponse> listAll() {
        return mapAll(alumnusRepository.findAll());
    }

    @Transactional(readOnly = true)
    public List<AlumnusResponse> listByField(String fieldName) {
        return mapAll((fieldName != null && !fieldName.isBlank())
                ? alumnusRepository.findByFieldNameContainingIgnoreCase(fieldName.trim())
                : alumnusRepository.findAll());
    }

    @Transactional
    public AlumnusResponse create(AlumnusRequest request, HttpServletRequest httpRequest) {
        Alumnus alumnus = new Alumnus();
        apply(alumnus, request);
        Alumnus saved = alumnusRepository.save(alumnus);
        auditService.log("CREATE", "Alumnus", saved.getId(),
                "Alumni " + saved.getFirstName() + " " + saved.getLastName(), httpRequest);
        return AlumnusResponse.from(saved);
    }

    /** Mise à jour du parcours : emploi, coordonnées et diplôme évoluent après la sortie. */
    @Transactional
    public AlumnusResponse update(Long id, AlumnusRequest request, HttpServletRequest httpRequest) {
        Alumnus alumnus = alumnusRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Alumni", id));
        apply(alumnus, request);
        Alumnus saved = alumnusRepository.save(alumnus);
        auditService.log("UPDATE", "Alumnus", id,
                "Mise à jour alumni " + saved.getFirstName() + " " + saved.getLastName(), httpRequest);
        return AlumnusResponse.from(saved);
    }

    @Transactional
    public void delete(Long id, HttpServletRequest httpRequest) {
        Alumnus alumnus = alumnusRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Alumni", id));
        auditService.log("DELETE", "Alumnus", id, "Suppression alumni " + alumnus.getFirstName(), httpRequest);
        alumnusRepository.delete(alumnus);
    }

    private void apply(Alumnus alumnus, AlumnusRequest request) {
        if (request.getFirstName() == null || request.getFirstName().isBlank()
                || request.getLastName() == null || request.getLastName().isBlank()) {
            throw new BusinessException("Le prénom et le nom sont obligatoires");
        }
        Student student = null;
        if (request.getStudentId() != null) {
            student = studentRepository.findById(request.getStudentId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Élève", request.getStudentId()));
        }
        alumnus.setStudent(student);
        alumnus.setFirstName(request.getFirstName().trim());
        alumnus.setLastName(request.getLastName().trim());
        alumnus.setFieldName(trimToNull(request.getFieldName()));
        alumnus.setDiploma(request.getDiploma());
        alumnus.setAcademicYear(request.getAcademicYear());
        alumnus.setEmail(request.getEmail());
        alumnus.setPhone(request.getPhone());
        alumnus.setCurrentJob(request.getCurrentJob());
        alumnus.setCompany(request.getCompany());
        alumnus.setNotes(request.getNotes());
    }

    private String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private List<AlumnusResponse> mapAll(List<Alumnus> alumni) {
        return alumni.stream().map(AlumnusResponse::from).toList();
    }
}
