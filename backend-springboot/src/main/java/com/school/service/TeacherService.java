package com.school.service;

import com.school.dto.request.TeacherRequest;
import com.school.dto.response.PageResponse;
import com.school.dto.response.TeacherResponse;
import com.school.entity.Teacher;
import com.school.enums.TeacherStatus;
import com.school.exception.ResourceNotFoundException;
import com.school.mapper.TeacherMapper;
import com.school.repository.TeacherRepository;
import com.school.utils.CodeGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

/**
 * Module enseignants : profils, contrats, salaires, comptes.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TeacherService {

    private final TeacherRepository teacherRepository;
    private final TeacherMapper teacherMapper;
    private final AuthService authService;
    private final AuditService auditService;
    private final FileStorageService fileStorageService;

    public PageResponse<TeacherResponse> search(String search, TeacherStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("lastName").ascending());
        Page<Teacher> result = teacherRepository.search(search, status, pageable);
        return PageResponse.from(result, TeacherResponse::from);
    }

    public TeacherResponse getById(Long id) {
        return TeacherResponse.from(findById(id));
    }

    @Transactional
    public TeacherResponse create(TeacherRequest request, HttpServletRequest httpRequest) {
        Teacher teacher = teacherMapper.toEntity(request);
        teacher.setEmployeeNo(generateEmployeeNo());
        teacher.setStatus(TeacherStatus.ACTIVE);
        teacher.setSalary(request.getSalary() != null ? request.getSalary() : BigDecimal.ZERO);

        if (Boolean.TRUE.equals(request.getCreateUserAccount())) {
            teacher.setUser(authService.createLinkedAccount(
                    request.getUsername() != null ? request.getUsername() : defaultUsername(request),
                    request.getPassword() != null ? request.getPassword() : "Enseignant@123",
                    request.getEmail(),
                    request.getFirstName(), request.getLastName(), "ENSEIGNANT"));
        }

        Teacher saved = teacherRepository.save(teacher);
        auditService.log("CREATE", "Teacher", saved.getId(),
                "Embauche enseignant " + saved.getFullName() + " (" + saved.getEmployeeNo() + ")", httpRequest);
        return TeacherResponse.from(saved);
    }

    @Transactional
    public TeacherResponse update(Long id, TeacherRequest request, HttpServletRequest httpRequest) {
        Teacher teacher = findById(id);
        teacherMapper.updateEntity(request, teacher);
        if (request.getSalary() != null) {
            teacher.setSalary(request.getSalary());
        }
        if (Boolean.TRUE.equals(request.getCreateUserAccount())) {
            teacher.setUser(authService.createLinkedAccount(
                    request.getUsername() != null ? request.getUsername() : defaultUsername(request),
                    request.getPassword() != null ? request.getPassword() : "Enseignant@123",
                    request.getEmail(),
                    request.getFirstName(), request.getLastName(), "ENSEIGNANT"));
        }
        Teacher saved = teacherRepository.save(teacher);
        auditService.log("UPDATE", "Teacher", saved.getId(),
                "Modification du profil de " + saved.getFullName(), httpRequest);
        return TeacherResponse.from(saved);
    }

    @Transactional
    public TeacherResponse updateStatus(Long id, TeacherStatus status, HttpServletRequest httpRequest) {
        Teacher teacher = findById(id);
        teacher.setStatus(status);
        auditService.log("UPDATE", "Teacher", id, "Statut changé : " + status, httpRequest);
        return TeacherResponse.from(teacherRepository.save(teacher));
    }

    @Transactional
    public TeacherResponse uploadPhoto(Long id, MultipartFile file) {
        Teacher teacher = findById(id);
        teacher.setPhoto(fileStorageService.store(file, "teachers"));
        return TeacherResponse.from(teacherRepository.save(teacher));
    }

    @Transactional
    public void delete(Long id, HttpServletRequest httpRequest) {
        Teacher teacher = findById(id);
        auditService.log("DELETE", "Teacher", id,
                "Suppression du profil de " + teacher.getFullName(), httpRequest);
        teacherRepository.delete(teacher);
    }

    public Teacher findById(Long id) {
        return teacherRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Enseignant", id));
    }

    private String generateEmployeeNo() {
        long count = teacherRepository.count() + 1;
        String no = CodeGenerator.teacherEmployeeNo(count);
        while (teacherRepository.existsByEmployeeNo(no)) {
            no = CodeGenerator.teacherEmployeeNo(++count);
        }
        return no;
    }

    private String defaultUsername(TeacherRequest r) {
        return (r.getFirstName() + "." + r.getLastName())
                .toLowerCase().replaceAll("[^a-z0-9.]", "");
    }
}