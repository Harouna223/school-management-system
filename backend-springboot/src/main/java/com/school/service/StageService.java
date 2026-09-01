package com.school.service;

import com.school.entity.Stage;
import com.school.entity.Student;
import com.school.entity.Teacher;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.StageRepository;
import com.school.repository.StudentRepository;
import com.school.repository.TeacherRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestion des stages universitaires.
 */
@Service
@RequiredArgsConstructor
public class StageService {

    private final StageRepository stageRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<Stage> listAll() {
        return stageRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Stage> listByStudent(Long studentId) {
        return stageRepository.findByStudentIdOrderByStartDateDesc(studentId);
    }

    @Transactional
    public Stage create(Stage stage, HttpServletRequest httpRequest) {
        validateAndResolve(stage);
        Stage saved = stageRepository.save(stage);
        auditService.log("CREATE", "Stage", saved.getId(),
                "Stage " + saved.getCompany() + " pour " + saved.getStudent().getFullName(), httpRequest);
        return saved;
    }

    @Transactional
    public Stage update(Long id, Stage stage, HttpServletRequest httpRequest) {
        Stage existing = stageRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Stage", id));
        validateAndResolve(stage);
        stage.setId(id);
        Stage saved = stageRepository.save(stage);
        auditService.log("UPDATE", "Stage", id,
                "Mise à jour stage " + existing.getCompany(), httpRequest);
        return saved;
    }

    @Transactional
    public void delete(Long id, HttpServletRequest httpRequest) {
        Stage stage = stageRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Stage", id));
        auditService.log("DELETE", "Stage", id, "Suppression stage " + stage.getCompany(), httpRequest);
        stageRepository.delete(stage);
    }

    private void validateAndResolve(Stage stage) {
        if (stage.getCompany() == null || stage.getCompany().isBlank()) {
            throw new BusinessException("L'entreprise est obligatoire");
        }
        if (stage.getStudent() == null || stage.getStudent().getId() == null) {
            throw new BusinessException("L'étudiant est obligatoire");
        }
        Student student = studentRepository.findById(stage.getStudent().getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Élève", stage.getStudent().getId()));
        stage.setStudent(student);
        if (stage.getSupervisor() != null && stage.getSupervisor().getId() != null) {
            Teacher supervisor = teacherRepository.findById(stage.getSupervisor().getId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Enseignant", stage.getSupervisor().getId()));
            stage.setSupervisor(supervisor);
        }
    }
}