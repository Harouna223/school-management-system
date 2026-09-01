package com.school.service;

import com.school.dto.request.ExamRequest;
import com.school.dto.response.ExamResponse;
import com.school.dto.response.PageResponse;
import com.school.entity.Exam;
import com.school.enums.ExamStatus;
import com.school.enums.Term;
import com.school.exception.ResourceNotFoundException;
import com.school.mapper.ExamMapper;
import com.school.repository.ExamRepository;
import jakarta.servlet.http.HttpServletRequest;
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

    @Transactional(readOnly = true)
    public PageResponse<ExamResponse> search(String search, Long classId, Long subjectId,
                                             Term term, ExamStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("examDate").descending());
        Page<Exam> result = examRepository.search(search, classId, subjectId, term, status, pageable);
        return PageResponse.from(result, ExamResponse::from);
    }

    @Transactional(readOnly = true)
    public List<ExamResponse> listByClass(Long classId) {
        return examRepository.findBySchoolClassId(classId).stream()
                .map(ExamResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ExamResponse getById(Long id) {
        return ExamResponse.from(findById(id));
    }

    @Transactional
    public ExamResponse create(ExamRequest request, HttpServletRequest httpRequest) {
        Exam exam = examMapper.toEntity(request);
        exam.setSchoolClass(classService.findById(request.getClassId()));
        exam.setSubject(subjectService.findById(request.getSubjectId()));
        exam.setStatus(request.getStatus() != null ? request.getStatus() : ExamStatus.PLANNED);
        exam.setCoefficient(request.getCoefficient() != null ? request.getCoefficient() : 1);
        Exam saved = examRepository.save(exam);
        auditService.log("CREATE", "Exam", saved.getId(), "Planification évaluation " + saved.getName(), httpRequest);
        return ExamResponse.from(saved);
    }

    @Transactional
    public ExamResponse update(Long id, ExamRequest request, HttpServletRequest httpRequest) {
        Exam exam = findById(id);
        examMapper.updateEntity(request, exam);
        exam.setSchoolClass(classService.findById(request.getClassId()));
        exam.setSubject(subjectService.findById(request.getSubjectId()));
        if (request.getCoefficient() != null) {
            exam.setCoefficient(request.getCoefficient());
        }
        if (request.getStatus() != null) {
            exam.setStatus(request.getStatus());
        }
        auditService.log("UPDATE", "Exam", id, "Modification évaluation " + exam.getName(), httpRequest);
        return ExamResponse.from(examRepository.save(exam));
    }

    @Transactional
    public ExamResponse changeStatus(Long id, ExamStatus status, HttpServletRequest httpRequest) {
        Exam exam = findById(id);
        exam.setStatus(status);
        auditService.log("DELIBERATE", "Exam", id, "Statut évaluation -> " + status, httpRequest);
        return ExamResponse.from(examRepository.save(exam));
    }

    @Transactional
    public void delete(Long id, HttpServletRequest httpRequest) {
        Exam exam = findById(id);
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