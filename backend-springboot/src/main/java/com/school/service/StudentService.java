package com.school.service;

import com.school.dto.request.StudentRequest;
import com.school.dto.response.PageResponse;
import com.school.dto.response.StudentHistoryResponse;
import com.school.dto.response.StudentResponse;
import com.school.entity.Parent;
import com.school.entity.SchoolClass;
import com.school.entity.Student;
import com.school.entity.StudentHistory;
import com.school.enums.EducationCycle;
import com.school.enums.StudentHistoryAction;
import com.school.enums.StudentStatus;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.mapper.StudentMapper;
import com.school.repository.ParentRepository;
import com.school.repository.SchoolClassRepository;
import com.school.repository.StudentHistoryRepository;
import com.school.repository.StudentRepository;
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

import java.time.LocalDate;
import java.util.List;

/**
 * Module élèves : inscription, matricule auto, dossiers, recherche, pagination, photos.
 */
@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final StudentHistoryRepository studentHistoryRepository;
    private final StudentMapper studentMapper;
    private final AuthService authService;
    private final AuditService auditService;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public PageResponse<StudentResponse> search(String search, Long classId,
                                                StudentStatus status, EducationCycle cycle,
                                                int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("lastName").ascending());
        Page<Student> result = studentRepository.search(search, classId, status, cycle, pageable);
        return PageResponse.from(result, StudentResponse::from);
    }

    @Transactional(readOnly = true)
    public StudentResponse getById(Long id) {
        return StudentResponse.from(findById(id));
    }

    @Transactional
    public StudentResponse create(StudentRequest request, HttpServletRequest httpRequest) {
        if (request.getEnrollmentDate() == null) {
            request.setEnrollmentDate(LocalDate.now());
        }

        Student student = studentMapper.toEntity(request);
        student.setMatricule(generateMatricule());
        student.setStatus(StudentStatus.ACTIVE);
        applyClassAndCycle(student, request.getClassId(), request.getEducationCycle());
        student.setEnrollmentDate(request.getEnrollmentDate() != null
                ? request.getEnrollmentDate() : LocalDate.now());

        // Parent : existant ou création automatique
        Parent parent = null;
        if (request.getParentId() != null) {
            parent = parentRepository.findById(request.getParentId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Parent", request.getParentId()));
        } else if (hasParentData(request)) {
            parent = createParent(request);
        }
        if (parent != null) {
            student.setParent(linkParentAccount(parent, request));
        }

        // Compte utilisateur optionnel
        if (Boolean.TRUE.equals(request.getCreateUserAccount())) {
            student.setUser(authService.createLinkedAccount(
                    request.getUsername() != null ? request.getUsername() : defaultUsername(request),
                    request.getPassword() != null ? request.getPassword() : "Eleve@123",
                    request.getEmail(),
                    request.getFirstName(), request.getLastName(), "ELEVE"));
        }

        Student saved = studentRepository.save(student);
        auditService.log("CREATE", "Student", saved.getId(),
                "Inscription élève " + saved.getFullName() + " (" + saved.getMatricule() + ")", httpRequest);
        return StudentResponse.from(saved);
    }

    @Transactional
    public StudentResponse update(Long id, StudentRequest request, HttpServletRequest httpRequest) {
        Student student = findById(id);
        studentMapper.updateEntity(request, student);
        applyClassAndCycle(student, request.getClassId(), request.getEducationCycle());
        if (request.getEnrollmentDate() != null) {
            student.setEnrollmentDate(request.getEnrollmentDate());
        }
        if (request.getParentId() != null) {
            student.setParent(parentRepository.findById(request.getParentId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Parent", request.getParentId())));
        } else if (hasParentData(request)) {
            Parent parent = student.getParent() != null ? student.getParent() : createParent(request);
            parent = applyParentData(parent, request);
            student.setParent(linkParentAccount(parent, request));
        }
        if (Boolean.TRUE.equals(request.getCreateUserAccount())) {
            student.setUser(authService.createLinkedAccount(
                    request.getUsername() != null ? request.getUsername() : defaultUsername(request),
                    request.getPassword() != null ? request.getPassword() : "Eleve@123",
                    request.getEmail(),
                    request.getFirstName(), request.getLastName(), "ELEVE"));
        }
        Student saved = studentRepository.save(student);
        auditService.log("UPDATE", "Student", saved.getId(),
                "Modification du dossier de " + saved.getFullName(), httpRequest);
        return StudentResponse.from(saved);
    }

    @Transactional
    public StudentResponse uploadPhoto(Long id, MultipartFile file) {
        Student student = findById(id);
        student.setPhoto(fileStorageService.store(file, "students"));
        return StudentResponse.from(studentRepository.save(student));
    }

    @Transactional
    public void delete(Long id, HttpServletRequest httpRequest) {
        Student student = findById(id);
        try {
            auditService.log("DELETE", "Student", id,
                    "Suppression du dossier de " + student.getFullName(), httpRequest);
            studentRepository.delete(student);
            studentRepository.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new com.school.exception.BusinessException(
                    "Impossible de supprimer cet élève : des notes, bulletins, factures ou présences "
                            + "lui sont associés. Utilisez la fonction « Radier » pour clôturer son dossier.");
        }
    }

    // ------------------------------------------------------------------
    // Parcours scolaire : transfert, radiation, réinscription, historique
    // ------------------------------------------------------------------

    @Transactional
    public StudentResponse transfer(Long id, Long newClassId, String reason, HttpServletRequest httpRequest) {
        Student student = findById(id);
        if (student.getStatus() == StudentStatus.RADIATED) {
            throw new BusinessException("Impossible de transférer un élève radié");
        }
        SchoolClass newClass = getClass(newClassId);
        String fromClass = student.getSchoolClass() != null ? student.getSchoolClass().getName() : null;
        String toClass = newClass.getName();
        if (fromClass != null && fromClass.equals(toClass)) {
            throw new BusinessException("L'élève est déjà dans la classe " + toClass);
        }
        student.setSchoolClass(newClass);
        student.setStatus(StudentStatus.ACTIVE);
        studentRepository.save(student);
        recordHistory(student, StudentHistoryAction.TRANSFER, fromClass, toClass, reason, httpRequest);
        auditService.log("TRANSFER", "Student", id,
                "Transfert de " + student.getFullName() + " (" + fromClass + " → " + toClass + ")", httpRequest);
        return StudentResponse.from(student);
    }

    @Transactional
    public StudentResponse radiate(Long id, String reason, HttpServletRequest httpRequest) {
        Student student = findById(id);
        if (student.getStatus() == StudentStatus.RADIATED) {
            throw new BusinessException("Cet élève est déjà radié");
        }
        student.setStatus(StudentStatus.RADIATED);
        studentRepository.save(student);
        recordHistory(student, StudentHistoryAction.RADIATION,
                student.getSchoolClass() != null ? student.getSchoolClass().getName() : null,
                null, reason, httpRequest);
        auditService.log("RADIATION", "Student", id,
                "Radiation de " + student.getFullName(), httpRequest);
        return StudentResponse.from(student);
    }

    @Transactional
    public StudentResponse reinscribe(Long id, Long classId, String reason, HttpServletRequest httpRequest) {
        Student student = findById(id);
        if (student.getStatus() != StudentStatus.RADIATED && student.getStatus() != StudentStatus.INACTIVE) {
            throw new BusinessException("Seul un élève radié ou inactif peut être réinscrit");
        }
        if (classId != null) {
            student.setSchoolClass(getClass(classId));
        }
        student.setStatus(StudentStatus.ACTIVE);
        student.setEnrollmentDate(LocalDate.now());
        studentRepository.save(student);
        recordHistory(student, StudentHistoryAction.REINSCRIPTION,
                null, student.getSchoolClass() != null ? student.getSchoolClass().getName() : null,
                reason, httpRequest);
        auditService.log("REINSCRIPTION", "Student", id,
                "Réinscription de " + student.getFullName(), httpRequest);
        return StudentResponse.from(student);
    }

    @Transactional(readOnly = true)
    public List<StudentHistoryResponse> historyOf(Long studentId) {
        findById(studentId);
        return studentHistoryRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(StudentHistoryResponse::from)
                .toList();
    }

    private void recordHistory(Student student, StudentHistoryAction action,
                               String fromClass, String toClass, String reason,
                               HttpServletRequest httpRequest) {
        String recordedBy = httpRequest != null && httpRequest.getUserPrincipal() != null
                ? httpRequest.getUserPrincipal().getName() : "Système";
        studentHistoryRepository.save(StudentHistory.builder()
                .student(student)
                .action(action)
                .fromClass(fromClass)
                .toClass(toClass)
                .reason(reason)
                .recordedBy(recordedBy)
                .build());
    }

    public String generateMatricule() {
        long count = studentRepository.count() + 1;
        String matricule = CodeGenerator.studentMatricule(count);
        while (studentRepository.existsByMatricule(matricule)) {
            matricule = CodeGenerator.studentMatricule(++count);
        }
        return matricule;
    }

    public Student findById(Long id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Élève", id));
    }

    private SchoolClass getClass(Long classId) {
        return schoolClassRepository.findById(classId)
                .orElseThrow(() -> ResourceNotFoundException.of("Classe", classId));
    }

    /**
     * Applique la classe (optionnelle) et déduit le cycle d'enseignement.
     * Si le cycle n'est pas fourni explicitement, il est déduit de la classe ;
     * à défaut, la classe est réinitialisée et le cycle reste inchangé ou null.
     */
    private void applyClassAndCycle(Student student, Long classId, EducationCycle cycle) {
        if (classId != null) {
            SchoolClass schoolClass = getClass(classId);
            student.setSchoolClass(schoolClass);
            if (cycle == null) {
                cycle = schoolClass.getLevel() != null
                        ? schoolClass.getLevel().getEducationCycle() : null;
            }
            student.setEducationCycle(cycle);
        } else {
            student.setSchoolClass(null);
            if (cycle != null) {
                student.setEducationCycle(cycle);
            }
        }
    }

    private boolean hasParentData(StudentRequest r) {
        return r.getParentFirstName() != null || r.getParentLastName() != null
                || r.getParentPhone() != null || r.getParentEmail() != null;
    }

    /**
     * Recopie les champs parent du formulaire sur l'entité parent (existant ou
     * récupéré), afin qu'une modification du nom/téléphone/email du parent soit
     * réellement persistée.
     */
    private Parent applyParentData(Parent parent, StudentRequest r) {
        boolean changed = false;
        if (r.getParentFirstName() != null) {
            parent.setFirstName(r.getParentFirstName());
            changed = true;
        }
        if (r.getParentLastName() != null) {
            parent.setLastName(r.getParentLastName());
            changed = true;
        }
        if (r.getParentPhone() != null) {
            parent.setPhone(r.getParentPhone());
            changed = true;
        }
        if (r.getParentEmail() != null) {
            parent.setEmail(r.getParentEmail());
            changed = true;
        }
        if (r.getParentProfession() != null) {
            parent.setProfession(r.getParentProfession());
            changed = true;
        }
        return changed ? parentRepository.save(parent) : parent;
    }

    private Parent createParent(StudentRequest r) {
        if (r.getParentPhone() != null && !r.getParentPhone().isBlank()) {
            Parent existing = parentRepository.findByPhone(r.getParentPhone()).orElse(null);
            if (existing != null) {
                return existing;
            }
        }
        if (r.getParentEmail() != null && !r.getParentEmail().isBlank()) {
            Parent existing = parentRepository.findByEmail(r.getParentEmail()).orElse(null);
            if (existing != null) {
                return existing;
            }
        }
        Parent parent = Parent.builder()
                .firstName(r.getParentFirstName() != null ? r.getParentFirstName() : "Parent")
                .lastName(r.getParentLastName() != null ? r.getParentLastName() : r.getLastName())
                .phone(r.getParentPhone())
                .email(r.getParentEmail())
                .profession(r.getParentProfession())
                .build();
        return parentRepository.save(parent);
    }

    /**
     * Crée le compte utilisateur PARENT (si demandé) et le lie au dossier parent.
     */
    private Parent linkParentAccount(Parent parent, StudentRequest r) {
        if (Boolean.TRUE.equals(r.getCreateParentAccount()) && parent.getUser() == null) {
            String username = r.getParentUsername() != null && !r.getParentUsername().isBlank()
                    ? r.getParentUsername() : defaultParentUsername(parent);
            String password = r.getParentPassword() != null && !r.getParentPassword().isBlank()
                    ? r.getParentPassword() : "Parent@123";
            parent.setUser(authService.createLinkedAccount(
                    username, password, parent.getEmail(),
                    parent.getFirstName(), parent.getLastName(), "PARENT"));
            return parentRepository.save(parent);
        }
        return parent;
    }

    private String defaultParentUsername(Parent parent) {
        return (parent.getFirstName() + "." + parent.getLastName())
                .toLowerCase().replaceAll("[^a-z0-9.]", "");
    }

    private String defaultUsername(StudentRequest r) {
        return (r.getFirstName() + "." + r.getLastName())
                .toLowerCase().replaceAll("[^a-z0-9.]", "");
    }
}