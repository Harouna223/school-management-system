package com.school.service;

import com.school.dto.request.ExamRequest;
import com.school.dto.response.ExamResponse;
import com.school.dto.response.PageResponse;
import com.school.entity.Exam;
import com.school.enums.ExamStatus;
import com.school.enums.Term;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.mapper.ExamMapper;
import com.school.repository.ExamRepository;
import com.school.repository.GradeRepository;
import jakarta.servlet.http.HttpServletRequest;
import com.school.utils.Pagination;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Module examens : planification, statuts, délibérations.
 */
@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository examRepository;
    private final ExamMapper examMapper;
    private final ClassService classService;
    private final SubjectService subjectService;
    private final AuditService auditService;
    private final GradeRepository gradeRepository;
    private final AcademicCalendarService academicCalendarService;
    private final AccessControlService accessControlService;

    @Transactional(readOnly = true)
    public PageResponse<ExamResponse> search(String search, Long classId, Long subjectId,
                                             Term term, ExamStatus status, int page, int size) {
        Pageable pageable = Pagination.of(page, size, Sort.by("examDate").descending());
        Page<Exam> result;
        if (accessControlService.isRestrictedTeacher()) {
            // Un enseignant ne liste que les évaluations de ses classes ; une
            // classe hors périmètre est refusée même demandée explicitement.
            if (classId != null) {
                accessControlService.assertCanAccessClass(classId);
            }
            List<Long> classIds = accessControlService.teacherClassIds();
            result = classIds == null || classIds.isEmpty()
                    ? Page.empty(pageable)
                    : examRepository.searchByClassIds(classIds, search, classId, subjectId,
                            term, status, pageable);
        } else {
            result = examRepository.search(search, classId, subjectId, term, status, pageable);
        }
        return PageResponse.from(result, ExamResponse::from);
    }

    @Transactional(readOnly = true)
    public List<ExamResponse> listByClass(Long classId) {
        return listByClass(classId, null);
    }

    /**
     * Évaluations d'une classe.
     *
     * @param yearId année scolaire ; {@code null} = toutes les années (comportement
     *               historique, réservé aux écrans d'administration qui doivent
     *               pouvoir consulter une année passée)
     */
    @Transactional(readOnly = true)
    public List<ExamResponse> listByClass(Long classId, Long yearId) {
        accessControlService.assertCanAccessClass(classId);
        List<Exam> exams = examRepository.findBySchoolClassId(classId);
        if (yearId == null) {
            return exams.stream().map(ExamResponse::from).toList();
        }
        String year = academicCalendarService.resolveYear(yearId, null, null).getLabel();
        return exams.stream()
                .filter(e -> year.equals(e.getAcademicYear()))
                .map(ExamResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ExamResponse getById(Long id) {
        Exam exam = findById(id);
        if (exam.getSchoolClass() != null) {
            accessControlService.assertCanAccessClass(exam.getSchoolClass().getId());
        }
        return ExamResponse.from(exam);
    }

    @Transactional
    public ExamResponse create(ExamRequest request, HttpServletRequest httpRequest) {
        accessControlService.assertCanTeachSubjectInClass(request.getClassId(), request.getSubjectId());
        Exam exam = examMapper.toEntity(request);
        exam.setSchoolClass(classService.findById(request.getClassId()));
        exam.setSubject(subjectService.findById(request.getSubjectId()));
        // L'année scolaire ne peut plus venir « en texte libre » : elle doit
        // correspondre à une année configurée, sinon des notes estampillées d'une
        // année inexistante faussaient toutes les moyennes.
        exam.setAcademicYear(resolveAcademicYearLabel(request, exam));
        exam.setStatus(request.getStatus() != null ? request.getStatus() : ExamStatus.PLANNED);
        exam.setCoefficient(request.getCoefficient() != null ? request.getCoefficient() : 1);
        Exam saved = examRepository.save(exam);
        auditService.log("CREATE", "Exam", saved.getId(), "Planification évaluation " + saved.getName(), httpRequest);
        return ExamResponse.from(saved);
    }

    @Transactional
    public ExamResponse update(Long id, ExamRequest request, HttpServletRequest httpRequest) {
        Exam exam = findById(id);
        // Ni l'évaluation d'origine ni sa nouvelle cible ne doivent sortir du
        // périmètre de l'enseignant.
        if (exam.getSchoolClass() != null) {
            accessControlService.assertCanAccessClass(exam.getSchoolClass().getId());
        }
        accessControlService.assertCanTeachSubjectInClass(request.getClassId(), request.getSubjectId());
        examMapper.updateEntity(request, exam);
        exam.setSchoolClass(classService.findById(request.getClassId()));
        exam.setSubject(subjectService.findById(request.getSubjectId()));
        exam.setAcademicYear(resolveAcademicYearLabel(request, exam));
        if (request.getCoefficient() != null) {
            exam.setCoefficient(request.getCoefficient());
        }
        if (request.getStatus() != null) {
            exam.setStatus(request.getStatus());
        }
        auditService.log("UPDATE", "Exam", id, "Modification évaluation " + exam.getName(), httpRequest);
        return ExamResponse.from(examRepository.save(exam));
    }

    /** Libellé d'année scolaire validé pour une évaluation. */
    private String resolveAcademicYearLabel(ExamRequest request, Exam exam) {
        String label = request.getAcademicYear() != null ? request.getAcademicYear() : exam.getAcademicYear();
        return academicCalendarService.resolveYear(null, label, exam.getExamDate()).getLabel();
    }

    @Transactional
    public ExamResponse changeStatus(Long id, ExamStatus status, HttpServletRequest httpRequest) {
        Exam exam = findById(id);
        if (exam.getSchoolClass() != null) {
            accessControlService.assertCanAccessClass(exam.getSchoolClass().getId());
        }
        exam.setStatus(status);
        auditService.log("DELIBERATE", "Exam", id, "Statut évaluation -> " + status, httpRequest);
        return ExamResponse.from(examRepository.save(exam));
    }

    @Transactional
    public void delete(Long id, HttpServletRequest httpRequest) {
        Exam exam = findById(id);
        if (exam.getSchoolClass() != null) {
            accessControlService.assertCanAccessClass(exam.getSchoolClass().getId());
        }
        // Les notes d'une évaluation sont supprimées EN CASCADE par la base
        // (fk_grades_exam ON DELETE CASCADE) : sans cette garde, supprimer une
        // évaluation faisait disparaître silencieusement toutes ses notes.
        long notes = gradeRepository.countByExamId(id);
        if (notes > 0) {
            throw new BusinessException("Impossible de supprimer : " + notes
                    + " note(s) sont enregistrées sur cette évaluation. "
                    + "Supprimez d'abord ces notes ou conservez l'évaluation.");
        }
        auditService.log("DELETE", "Exam", id, "Suppression évaluation " + exam.getName(), httpRequest);
        examRepository.delete(exam);
    }

    public List<Exam> upcoming(LocalDate from, LocalDate to) {
        return examRepository.findByExamDateBetween(from, to);
    }

    public Exam findById(Long id) {
        return examRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Évaluation", id));
    }
}