package com.school.service;

import com.school.dto.request.AssignmentRequest;
import com.school.dto.request.SubjectRequest;
import com.school.dto.response.AssignmentResponse;
import com.school.dto.response.PageResponse;
import com.school.dto.response.SubjectResponse;
import com.school.entity.Subject;
import com.school.entity.SubjectAssignment;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.mapper.SubjectMapper;
import com.school.repository.SubjectAssignmentRepository;
import com.school.repository.SubjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Module matières : CRUD, coefficients, affectations aux enseignants.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final SubjectAssignmentRepository assignmentRepository;
    private final SubjectMapper subjectMapper;
    private final TeacherService teacherService;
    private final ClassService classService;
    private final AuditService auditService;

    public PageResponse<SubjectResponse> search(String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<Subject> result = subjectRepository.search(search, pageable);
        return PageResponse.from(result, SubjectResponse::from);
    }

    public List<SubjectResponse> findAll() {
        return subjectRepository.findAll(Sort.by("name")).stream()
                .map(SubjectResponse::from).toList();
    }

    public SubjectResponse getById(Long id) {
        return SubjectResponse.from(findById(id));
    }

    @Transactional
    public SubjectResponse create(SubjectRequest request, HttpServletRequest httpRequest) {
        if (subjectRepository.existsByCode(request.getCode())) {
            throw new BusinessException("Une matière porte déjà ce code : " + request.getCode());
        }
        Subject subject = subjectMapper.toEntity(request);
        Subject saved = subjectRepository.save(subject);
        auditService.log("CREATE", "Subject", saved.getId(), "Création matière " + saved.getName(), httpRequest);
        return SubjectResponse.from(saved);
    }

    @Transactional
    public SubjectResponse update(Long id, SubjectRequest request, HttpServletRequest httpRequest) {
        Subject subject = findById(id);
        subjectMapper.updateEntity(request, subject);
        auditService.log("UPDATE", "Subject", id, "Modification matière " + subject.getName(), httpRequest);
        return SubjectResponse.from(subjectRepository.save(subject));
    }

    @Transactional
    public void delete(Long id, HttpServletRequest httpRequest) {
        Subject subject = findById(id);
        auditService.log("DELETE", "Subject", id, "Suppression matière " + subject.getName(), httpRequest);
        subjectRepository.delete(subject);
    }

    public Subject findById(Long id) {
        return subjectRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Matière", id));
    }

    // --- Affectations ---

    public List<AssignmentResponse> listAssignments(Long teacherId, Long classId) {
        List<SubjectAssignment> assignments = teacherId != null
                ? assignmentRepository.findByTeacherId(teacherId)
                : classId != null ? assignmentRepository.findBySchoolClassId(classId)
                : assignmentRepository.findAll();
        return assignments.stream().map(AssignmentResponse::from).toList();
    }

    @Transactional
    public AssignmentResponse assign(AssignmentRequest request, HttpServletRequest httpRequest) {
        if (assignmentRepository.existsByTeacherIdAndSubjectIdAndSchoolClassId(
                request.getTeacherId(), request.getSubjectId(), request.getClassId())) {
            throw new BusinessException("Cet enseignant est déjà affecté à cette matière dans cette classe");
        }
        SubjectAssignment assignment = SubjectAssignment.builder()
                .teacher(teacherService.findById(request.getTeacherId()))
                .subject(findById(request.getSubjectId()))
                .schoolClass(classService.findById(request.getClassId()))
                .build();
        SubjectAssignment saved = assignmentRepository.save(assignment);
        auditService.log("ASSIGN", "SubjectAssignment", saved.getId(),
                "Affectation enseignant " + request.getTeacherId() + " -> matière " + request.getSubjectId(),
                httpRequest);
        return AssignmentResponse.from(saved);
    }

    @Transactional
    public void unassign(Long id, HttpServletRequest httpRequest) {
        SubjectAssignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Affectation", id));
        auditService.log("UNASSIGN", "SubjectAssignment", id,
                "Retrait affectation " + assignment.getTeacher().getFullName(), httpRequest);
        assignmentRepository.delete(assignment);
    }
}