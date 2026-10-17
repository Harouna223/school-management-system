package com.school.service;

import com.school.dto.request.TeacherRequest;
import com.school.dto.response.PageResponse;
import com.school.dto.response.TeacherResponse;
import com.school.entity.Teacher;
import com.school.entity.User;
import com.school.enums.TeacherStatus;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.mapper.TeacherMapper;
import com.school.repository.ContractRepository;
import com.school.repository.LeaveRepository;
import com.school.repository.PayrollRepository;
import com.school.repository.ScheduleRepository;
import com.school.repository.SubjectAssignmentRepository;
import com.school.repository.TeacherAttendanceRepository;
import com.school.repository.TeacherHourlyRateRepository;
import com.school.repository.TeacherMonthlyPaymentRepository;
import com.school.repository.TeacherRepository;
import com.school.repository.TeacherWorkHourRepository;
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

    // Gardes anti-cascade de delete() : la base propage la suppression d'un
    // enseignant a ces tables (fk_*_teacher ON DELETE CASCADE). Sans elles, un
    // enseignant sans creneau restait supprimable et perdait tout son
    // historique de paie sans un mot.
    private final ContractRepository contractRepository;
    private final PayrollRepository payrollRepository;
    private final TeacherMonthlyPaymentRepository monthlyPaymentRepository;
    private final TeacherWorkHourRepository workHourRepository;
    private final TeacherAttendanceRepository teacherAttendanceRepository;
    private final TeacherHourlyRateRepository hourlyRateRepository;
    private final LeaveRepository leaveRepository;
    private final SubjectAssignmentRepository subjectAssignmentRepository;
    private final ScheduleRepository scheduleRepository;

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

        String generatedPassword = null;
        String accountUsername = null;
        if (Boolean.TRUE.equals(request.getCreateUserAccount())) {
            String rawPassword = request.getPassword();
            if (rawPassword == null || rawPassword.isBlank()) {
                rawPassword = CodeGenerator.randomPassword();
                generatedPassword = rawPassword;
            }
            User account = authService.createLinkedAccount(
                    request.getUsername() != null ? request.getUsername() : defaultUsername(request),
                    rawPassword,
                    request.getEmail(),
                    request.getFirstName(), request.getLastName(), "ENSEIGNANT");
            if (account != null) {
                teacher.setUser(account);
                if (generatedPassword != null) {
                    accountUsername = account.getUsername();
                }
            }
        }

        Teacher saved = teacherRepository.save(teacher);
        TeacherResponse response = TeacherResponse.from(saved);
        if (generatedPassword != null) {
            response.setGeneratedPassword(generatedPassword);
        }
        if (accountUsername != null) {
            response.setAccountUsername(accountUsername);
        }
        auditService.log("CREATE", "Teacher", saved.getId(),
                "Embauche enseignant " + saved.getFullName() + " (" + saved.getEmployeeNo() + ")", httpRequest);
        return response;
    }

    @Transactional
    public TeacherResponse update(Long id, TeacherRequest request, HttpServletRequest httpRequest) {
        Teacher teacher = findById(id);
        teacherMapper.updateEntity(request, teacher);
        if (request.getSalary() != null) {
            teacher.setSalary(request.getSalary());
        }
        String generatedPassword = null;
        String accountUsername = null;
        if (Boolean.TRUE.equals(request.getCreateUserAccount())) {
            String rawPassword = request.getPassword();
            if (rawPassword == null || rawPassword.isBlank()) {
                rawPassword = CodeGenerator.randomPassword();
                generatedPassword = rawPassword;
            }
            User account = authService.createLinkedAccount(
                    request.getUsername() != null ? request.getUsername() : defaultUsername(request),
                    rawPassword,
                    request.getEmail(),
                    request.getFirstName(), request.getLastName(), "ENSEIGNANT");
            if (account != null) {
                teacher.setUser(account);
                if (generatedPassword != null) {
                    accountUsername = account.getUsername();
                }
            }
        }
        Teacher saved = teacherRepository.save(teacher);
        TeacherResponse response = TeacherResponse.from(saved);
        if (generatedPassword != null) {
            response.setGeneratedPassword(generatedPassword);
        }
        if (accountUsername != null) {
            response.setAccountUsername(accountUsername);
        }
        auditService.log("UPDATE", "Teacher", saved.getId(),
                "Modification du profil de " + saved.getFullName(), httpRequest);
        return response;
    }

    @Transactional
    public TeacherResponse updateStatus(Long id, TeacherStatus status, HttpServletRequest httpRequest) {
        Teacher teacher = findById(id);
        teacher.setStatus(status);
        auditService.log("UPDATE", "Teacher", id, "Statut changé : " + status, httpRequest);
        return TeacherResponse.from(teacherRepository.save(teacher));
    }

    /**
     * Applique un statut sans écrire de ligne d'audit : réservé à la
     * réconciliation planifiée des congés ({@code HrService}), qui journalise un
     * seul événement pour tout le lot afin de ne pas polluer le journal.
     */
    @Transactional
    public Teacher setStatusSilently(Long id, TeacherStatus status) {
        Teacher teacher = findById(id);
        teacher.setStatus(status);
        return teacherRepository.save(teacher);
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
        // Les FK en cascade (contrats, paies, paiements mensuels, heures, absences,
        // conges, affectations, creneaux) detruisaient tout l'historique RH sans
        // avertissement : un enseignant sans creneau restait supprimable, et ses
        // bulletins de paie disparaissaient avec lui.
        long dependances = countDependances(id);
        if (dependances > 0) {
            throw new BusinessException("Impossible de supprimer : " + dependances
                    + " enregistrement(s) (contrats, paie, heures, absences, conges, "
                    + "affectations) sont rattaches a cet enseignant. "
                    + "Passez son statut a INACTIVE pour conserver l'historique.");
        }
        auditService.log("DELETE", "Teacher", id,
                "Suppression du profil de " + teacher.getFullName(), httpRequest);
        teacherRepository.delete(teacher);
    }

    /** Total des enregistrements detruits en cascade avec l'enseignant. */
    private long countDependances(Long teacherId) {
        return contractRepository.countByTeacherId(teacherId)
                + payrollRepository.countByTeacherId(teacherId)
                + monthlyPaymentRepository.countByTeacherId(teacherId)
                + workHourRepository.countByTeacherId(teacherId)
                + teacherAttendanceRepository.countByTeacherId(teacherId)
                + hourlyRateRepository.countByTeacherId(teacherId)
                + leaveRepository.countByTeacherId(teacherId)
                + subjectAssignmentRepository.countByTeacherId(teacherId)
                + scheduleRepository.countByTeacherId(teacherId);
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