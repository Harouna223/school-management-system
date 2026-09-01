package com.school.service;

import com.school.entity.Memoire;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.MemoireRepository;
import com.school.repository.StudentRepository;
import com.school.repository.TeacherRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestion des mémoires et soutenances universitaires.
 */
@Service
@RequiredArgsConstructor
public class MemoireService {

    private final MemoireRepository memoireRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<Memoire> listAll() {
        return memoireRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Memoire> listByStudent(Long studentId) {
        return memoireRepository.findByStudentIdOrderByDefenseDateDesc(studentId);
    }

    @Transactional
    public Memoire create(Memoire memoire, HttpServletRequest httpRequest) {
        if (memoire.getSubject() == null || memoire.getSubject().isBlank()) {
            throw new BusinessException("Le sujet est obligatoire");
        }
        if (memoire.getStudent() == null || memoire.getStudent().getId() == null) {
            throw new BusinessException("L'étudiant est obligatoire");
        }
        studentRepository.findById(memoire.getStudent().getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Élève", memoire.getStudent().getId()));
        if (memoire.getDirector() != null && memoire.getDirector().getId() != null) {
            teacherRepository.findById(memoire.getDirector().getId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Enseignant", memoire.getDirector().getId()));
        }
        Memoire saved = memoireRepository.save(memoire);
        auditService.log("CREATE", "Memoire", saved.getId(),
                "Mémoire : " + saved.getSubject(), httpRequest);
        return saved;
    }

    @Transactional
    public Memoire update(Long id, Memoire memoire, HttpServletRequest httpRequest) {
        Memoire existing = memoireRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Mémoire", id));
        if (memoire.getSubject() != null) existing.setSubject(memoire.getSubject());
        if (memoire.getDefenseDate() != null) existing.setDefenseDate(memoire.getDefenseDate());
        if (memoire.getJury() != null) existing.setJury(memoire.getJury());
        if (memoire.getDefenseLocation() != null) existing.setDefenseLocation(memoire.getDefenseLocation());
        if (memoire.getGrade() != null) existing.setGrade(memoire.getGrade());
        if (memoire.getDecision() != null) existing.setDecision(memoire.getDecision());
        if (memoire.getStatus() != null) existing.setStatus(memoire.getStatus());
        if (memoire.getDirector() != null && memoire.getDirector().getId() != null) {
            teacherRepository.findById(memoire.getDirector().getId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Enseignant", memoire.getDirector().getId()));
            existing.setDirector(memoire.getDirector());
        }
        Memoire saved = memoireRepository.save(existing);
        auditService.log("UPDATE", "Memoire", id, "Mise à jour mémoire", httpRequest);
        return saved;
    }

    @Transactional
    public void delete(Long id, HttpServletRequest httpRequest) {
        Memoire memoire = memoireRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Mémoire", id));
        auditService.log("DELETE", "Memoire", id, "Suppression mémoire", httpRequest);
        memoireRepository.delete(memoire);
    }
}