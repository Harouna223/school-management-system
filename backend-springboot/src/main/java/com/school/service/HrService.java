package com.school.service;

import com.school.dto.request.ContractRequest;
import com.school.dto.request.LeaveRequest;
import com.school.dto.request.PayrollGenerateRequest;
import com.school.dto.response.ContractResponse;
import com.school.dto.response.LeaveResponse;
import com.school.dto.response.PayrollResponse;
import com.school.entity.Contract;
import com.school.entity.Leave;
import com.school.entity.Payroll;
import com.school.entity.Teacher;
import com.school.enums.ContractStatus;
import com.school.enums.LeaveStatus;
import com.school.enums.PayrollStatus;
import com.school.enums.TeacherStatus;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.ContractRepository;
import com.school.repository.LeaveRepository;
import com.school.repository.PayrollRepository;
import com.school.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Module RH : congés des enseignants, contrats, statuts.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class HrService {

    private final LeaveRepository leaveRepository;
    private final TeacherService teacherService;
    private final ContractRepository contractRepository;
    private final PayrollRepository payrollRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<LeaveResponse> listLeaves(Long teacherId, LeaveStatus status) {
        return leaveRepository.search(teacherId, status).stream()
                .map(LeaveResponse::from).toList();
    }

    @Transactional
    public LeaveResponse requestLeave(LeaveRequest request, HttpServletRequest httpRequest) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException("La date de fin doit être postérieure à la date de début");
        }
        Leave leave = Leave.builder()
                .teacher(teacherService.findById(request.getTeacherId()))
                .type(request.getType())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .reason(request.getReason())
                .status(LeaveStatus.PENDING)
                .build();
        Leave saved = leaveRepository.save(leave);
        auditService.log("LEAVE", "Leave", saved.getId(),
                "Demande de congé " + saved.getType(), httpRequest);
        return LeaveResponse.from(saved);
    }

    @Transactional
    public LeaveResponse decide(Long leaveId, LeaveStatus status, HttpServletRequest httpRequest) {
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> ResourceNotFoundException.of("Congé", leaveId));
        if (status == LeaveStatus.APPROVED || status == LeaveStatus.REJECTED) {
            leave.setStatus(status);
            leave.setApprovedBy(SecurityUtils.currentUser());
            if (status == LeaveStatus.APPROVED) {
                teacherService.updateStatus(leave.getTeacher().getId(),
                        com.school.enums.TeacherStatus.ON_LEAVE, httpRequest);
            }
        } else {
            throw new BusinessException("Décision invalide : APPROVED ou REJECTED attendu");
        }
        auditService.log("LEAVE_DECISION", "Leave", leaveId, "Congé -> " + status, httpRequest);
        return LeaveResponse.from(leaveRepository.save(leave));
    }

    /**
     * Réconciliation des statuts d'enseignants selon leurs congés approuvés (D26).
     * Un enseignant est « en congé » s'il a au moins un congé approuvé couvrant la
     * date du jour ; sinon il redevient « actif ». Cette méthode corrige aussi bien
     * l'oubli de bascule en fin de congé que les congés futur/anticipés.
     *
     * <p>Seuls les enseignants ayant au moins un congé approuvé sont concernés : un
     * statut ON_LEAVE posé manuellement (sans congé) n'est jamais écrasé.</p>
     *
     * @return le nombre de statuts modifiés
     */
    @Transactional
    public int reconcileTeacherLeaveStatus() {
        LocalDate today = LocalDate.now();
        List<Leave> approved = leaveRepository.findByStatus(LeaveStatus.APPROVED);
        if (approved.isEmpty()) {
            return 0;
        }
        // Un enseignant est « en congé » s'il a AU MOINS un congé approuvé couvrant aujourd'hui.
        Map<Long, Boolean> onLeaveNow = new HashMap<>();
        Map<Long, Teacher> teachersById = new HashMap<>();
        for (Leave leave : approved) {
            Teacher teacher = leave.getTeacher();
            teachersById.put(teacher.getId(), teacher);
            boolean coversToday = !leave.getStartDate().isAfter(today)
                    && !leave.getEndDate().isBefore(today);
            onLeaveNow.merge(teacher.getId(), coversToday, Boolean::logicalOr);
        }
        int updated = 0;
        for (Teacher teacher : teachersById.values()) {
            boolean shouldBeOnLeave = Boolean.TRUE.equals(onLeaveNow.get(teacher.getId()));
            if (shouldBeOnLeave && teacher.getStatus() != TeacherStatus.ON_LEAVE) {
                teacherService.setStatusSilently(teacher.getId(), TeacherStatus.ON_LEAVE);
                updated++;
            } else if (!shouldBeOnLeave && teacher.getStatus() == TeacherStatus.ON_LEAVE) {
                // Congé terminé (ou annulé) : l'enseignant redevient actif.
                teacherService.setStatusSilently(teacher.getId(), TeacherStatus.ACTIVE);
                updated++;
            }
        }
        if (updated > 0) {
            auditService.log("LEAVE_RECONCILE", "Teacher", null,
                    updated + " statut(s) enseignant ajusté(s) selon les congés", null);
        }
        return updated;
    }

    /** Réconciliation quotidienne des statuts de congé (exécution à 2 h du matin). */
    @Scheduled(cron = "0 0 2 * * *")
    public void scheduledReconcileTeacherLeaveStatus() {
        int updated = reconcileTeacherLeaveStatus();
        if (updated > 0) {
            log.info("Réconciliation des congés : {} statut(s) enseignant ajusté(s)", updated);
        }
    }

    // ---------- Contrats ----------

    @Transactional(readOnly = true)
    public List<ContractResponse> listContracts(Long teacherId, ContractStatus status) {
        return contractRepository.search(teacherId, status).stream()
                .map(ContractResponse::from).toList();
    }

    @Transactional
    public ContractResponse createContract(ContractRequest request, HttpServletRequest httpRequest) {
        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException("La date de fin doit être postérieure à la date de début");
        }
        Contract contract = Contract.builder()
                .teacher(teacherService.findById(request.getTeacherId()))
                .type(request.getType())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .baseSalary(request.getBaseSalary())
                .description(request.getDescription())
                .status(resolveStatus(request))
                .build();
        Contract saved = contractRepository.save(contract);
        auditService.log("CONTRACT", "Contract", saved.getId(),
                "Contrat " + saved.getType() + " pour " + saved.getTeacher().getFullName(), httpRequest);
        return ContractResponse.from(saved);
    }

    @Transactional
    public ContractResponse updateContract(Long id, ContractRequest request, HttpServletRequest httpRequest) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Contrat", id));
        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException("La date de fin doit être postérieure à la date de début");
        }
        contract.setType(request.getType());
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setBaseSalary(request.getBaseSalary());
        contract.setDescription(request.getDescription());
        contract.setStatus(resolveStatus(request));
        auditService.log("CONTRACT_UPDATE", "Contract", id,
                "Contrat modifié pour " + contract.getTeacher().getFullName(), httpRequest);
        return ContractResponse.from(contractRepository.save(contract));
    }

    @Transactional
    public void deleteContract(Long id, HttpServletRequest httpRequest) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Contrat", id));
        contractRepository.delete(contract);
        auditService.log("CONTRACT_DELETE", "Contract", id,
                "Contrat supprimé pour " + contract.getTeacher().getFullName(), httpRequest);
    }

    private ContractStatus resolveStatus(ContractRequest request) {
        if (request.getStatus() != null) {
            return request.getStatus();
        }
        if (request.getEndDate() != null && request.getEndDate().isBefore(LocalDate.now())) {
            return ContractStatus.EXPIRED;
        }
        return ContractStatus.ACTIVE;
    }

    // ---------- Paie ----------

    @Transactional(readOnly = true)
    public List<PayrollResponse> listPayrolls(Long teacherId, LocalDate monthDate, PayrollStatus status) {
        return payrollRepository.search(teacherId, monthDate, status).stream()
                .map(PayrollResponse::from).toList();
    }

    /**
     * Génération d'un bulletin de paie mensuel : salaire de base = dernier contrat
     * actif de l'enseignant, sinon salaire du profil. Net = base + primes - retenues.
     */
    @Transactional
    public PayrollResponse generatePayroll(PayrollGenerateRequest request, HttpServletRequest httpRequest) {
        if (request.getMonthDate().getDayOfMonth() != 1) {
            throw new BusinessException("Le mois doit être le premier jour du mois");
        }
        payrollRepository.findByTeacherIdAndMonthDate(request.getTeacherId(), request.getMonthDate())
                .ifPresent(existing -> {
                    throw new BusinessException("Un bulletin existe déjà pour cet enseignant ce mois-ci");
                });

        var teacher = teacherService.findById(request.getTeacherId());
        BigDecimal baseSalary = latestActiveSalary(teacher.getId())
                .orElse(teacher.getSalary());

        Payroll payroll = Payroll.builder()
                .teacher(teacher)
                .monthDate(request.getMonthDate())
                .baseSalary(baseSalary)
                .allowances(request.getAllowances() != null ? request.getAllowances() : BigDecimal.ZERO)
                .deductions(request.getDeductions() != null ? request.getDeductions() : BigDecimal.ZERO)
                .netSalary(baseSalary
                        .add(request.getAllowances() != null ? request.getAllowances() : BigDecimal.ZERO)
                        .subtract(request.getDeductions() != null ? request.getDeductions() : BigDecimal.ZERO))
                .status(PayrollStatus.PENDING)
                .build();
        Payroll saved = payrollRepository.save(payroll);
        auditService.log("PAYROLL", "Payroll", saved.getId(),
                "Bulletin de paie généré pour " + teacher.getFullName() + " (" + request.getMonthDate() + ")",
                httpRequest);
        return PayrollResponse.from(saved);
    }

    @Transactional
    public PayrollResponse markPayrollPaid(Long id, HttpServletRequest httpRequest) {
        Payroll payroll = payrollRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Bulletin de paie", id));
        if (payroll.getStatus() == PayrollStatus.PAID) {
            throw new BusinessException("Ce bulletin est déjà marqué comme payé");
        }
        payroll.setStatus(PayrollStatus.PAID);
        payroll.setPaidAt(java.time.LocalDateTime.now());
        auditService.log("PAYROLL_PAID", "Payroll", id,
                "Salaire payé à " + payroll.getTeacher().getFullName() + " (" + payroll.getMonthDate() + ")",
                httpRequest);
        return PayrollResponse.from(payrollRepository.save(payroll));
    }

    private java.util.Optional<BigDecimal> latestActiveSalary(Long teacherId) {
        return contractRepository.findByTeacherIdOrderByStartDateDesc(teacherId).stream()
                .filter(c -> c.getStatus() == ContractStatus.ACTIVE)
                .findFirst()
                .map(Contract::getBaseSalary);
    }
}