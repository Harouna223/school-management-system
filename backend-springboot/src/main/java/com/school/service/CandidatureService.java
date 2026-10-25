package com.school.service;

import com.school.dto.request.AdmissionEnrollmentRequest;
import com.school.dto.request.CandidatureRequest;
import com.school.dto.request.StudentRequest;
import com.school.dto.response.AdmissionEnrollmentResponse;
import com.school.dto.response.CandidatureResponse;
import com.school.dto.response.StudentResponse;
import com.school.entity.Candidature;
import com.school.entity.Student;
import com.school.enums.CandidatureStatus;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.CandidatureRepository;
import com.school.repository.StudentRepository;
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
    private final StudentRepository studentRepository;
    private final StudentService studentService;
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
        if (candidature.getStudent() != null && status != CandidatureStatus.INSCRITE) {
            throw new BusinessException("Cette candidature a déjà donné lieu à une inscription : "
                    + "son statut ne peut plus être modifié.");
        }
        candidature.setStatus(status);
        Candidature saved = candidatureRepository.save(candidature);
        auditService.log("UPDATE", "Candidature", id,
                "Statut candidature " + saved.getReference() + " → " + status, httpRequest);
        return CandidatureResponse.from(saved);
    }

    /**
     * Transforme une candidature acceptée en inscription : crée l'élève (avec son
     * matricule et son compte) puis rattache le dossier à cet élève.
     *
     * <p>Le chemin de création de l'élève n'est pas dupliqué : c'est
     * {@link StudentService#create} qui attribue le matricule, rattache la classe
     * et le cycle, et génère le compte utilisateur.</p>
     */
    @Transactional
    public AdmissionEnrollmentResponse enroll(Long id, AdmissionEnrollmentRequest request,
                                              HttpServletRequest httpRequest) {
        Candidature candidature = candidatureRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Candidature", id));
        if (candidature.getStudent() != null) {
            throw new BusinessException("Cette candidature a déjà été inscrite (matricule "
                    + candidature.getStudent().getMatricule() + ").");
        }
        if (candidature.getStatus() != CandidatureStatus.ACCEPTEE) {
            throw new BusinessException("Seule une candidature acceptée peut être inscrite "
                    + "(statut actuel : " + candidature.getStatus() + ").");
        }

        StudentResponse student = studentService.create(
                toStudentRequest(candidature, request), httpRequest);
        Student entity = studentRepository.findById(student.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Élève", student.getId()));

        candidature.setStudent(entity);
        candidature.setStatus(CandidatureStatus.INSCRITE);
        Candidature saved = candidatureRepository.save(candidature);
        auditService.log("UPDATE", "Candidature", id,
                "Inscription de " + saved.getFirstName() + " " + saved.getLastName()
                        + " (" + student.getMatricule() + ") depuis la candidature " + saved.getReference(),
                httpRequest);

        return AdmissionEnrollmentResponse.builder()
                .candidature(CandidatureResponse.from(saved))
                .student(student)
                .build();
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
        if (candidature.getStudent() != null) {
            throw new BusinessException("Impossible de supprimer une candidature déjà inscrite : "
                    + "elle justifie l'existence de l'élève " + candidature.getStudent().getMatricule() + ".");
        }
        auditService.log("DELETE", "Candidature", id, "Suppression candidature " + candidature.getReference(), httpRequest);
        candidatureRepository.delete(candidature);
    }

    /** Reprend les données du dossier ; seules les informations manquantes sont demandées. */
    private StudentRequest toStudentRequest(Candidature candidature, AdmissionEnrollmentRequest request) {
        return StudentRequest.builder()
                .firstName(candidature.getFirstName())
                .lastName(candidature.getLastName())
                .birthDate(candidature.getBirthDate())
                .email(candidature.getEmail())
                .phone(candidature.getPhone())
                .gender(request.getGender())
                .classId(request.getClassId())
                .educationCycle(request.getEducationCycle())
                .enrollmentDate(request.getEnrollmentDate())
                .build();
    }
}
