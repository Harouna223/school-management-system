package com.school.service;

import com.school.dto.request.StageRequest;
import com.school.dto.response.StageResponse;
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
 *
 * <p>Le service expose des {@link StageResponse} : la conversion a lieu dans la
 * transaction, seule façon d'accéder aux rattachements paresseux puisque
 * {@code open-in-view} est désactivé.</p>
 */
@Service
@RequiredArgsConstructor
public class StageService {

    private final StageRepository stageRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<StageResponse> listAll() {
        return stageRepository.findAllByOrderByStartDateDesc().stream()
                .map(StageResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StageResponse> listByStudent(Long studentId) {
        return stageRepository.findByStudentIdOrderByStartDateDesc(studentId).stream()
                .map(StageResponse::from)
                .toList();
    }

    @Transactional
    public StageResponse create(StageRequest request, HttpServletRequest httpRequest) {
        Stage stage = resolve(request);
        Stage saved = stageRepository.save(stage);
        auditService.log("CREATE", "Stage", saved.getId(),
                "Stage " + saved.getCompany() + " pour " + saved.getStudent().getFullName(), httpRequest);
        return StageResponse.from(saved);
    }

    @Transactional
    public StageResponse update(Long id, StageRequest request, HttpServletRequest httpRequest) {
        Stage existing = stageRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Stage", id));
        Stage stage = resolve(request);
        stage.setId(id);
        Stage saved = stageRepository.save(stage);
        auditService.log("UPDATE", "Stage", id,
                "Mise à jour stage " + existing.getCompany(), httpRequest);
        return StageResponse.from(saved);
    }

    @Transactional
    public void delete(Long id, HttpServletRequest httpRequest) {
        Stage stage = stageRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Stage", id));
        auditService.log("DELETE", "Stage", id, "Suppression stage " + stage.getCompany(), httpRequest);
        stageRepository.delete(stage);
    }

    /** Construit le stage et vérifie que l'élève et l'encadrant existent. */
    private Stage resolve(StageRequest request) {
        if (request.getCompany() == null || request.getCompany().isBlank()) {
            throw new BusinessException("L'entreprise est obligatoire");
        }
        if (request.getStudentId() == null) {
            throw new BusinessException("L'étudiant est obligatoire");
        }
        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> ResourceNotFoundException.of("Élève", request.getStudentId()));
        Teacher supervisor = null;
        if (request.getSupervisorId() != null) {
            supervisor = teacherRepository.findById(request.getSupervisorId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Enseignant", request.getSupervisorId()));
        }
        return Stage.builder()
                .student(student)
                .company(request.getCompany().trim())
                .companyContact(request.getCompanyContact())
                .subject(request.getSubject())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .supervisor(supervisor)
                .conventionRef(request.getConventionRef())
                .reportSubmitted(request.isReportSubmitted())
                .defenseDate(request.getDefenseDate())
                .grade(request.getGrade())
                .evaluation(request.getEvaluation())
                .status(request.getStatus())
                .build();
    }
}
