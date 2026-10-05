package com.school.service;

import com.school.dto.request.LmdEnrollmentRequest;
import com.school.dto.request.UeGradeRequest;
import com.school.dto.response.LmdDeliberationResult;
import com.school.entity.*;
import com.school.enums.AttendanceStatus;
import com.school.enums.EnrollmentStatus;
import com.school.enums.EvaluationType;
import com.school.enums.LmdDecision;
import com.school.enums.Mention;
import com.school.enums.UniversityLevel;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Module universitaire LMD : structure (facultés, départements, filières),
 * unités d'enseignement, notes UE, calculs ECTS et délibérations.
 */
@Service
@RequiredArgsConstructor
public class LmdService {

    private static final BigDecimal TEN = BigDecimal.TEN;
    private static final BigDecimal EIGHT = new BigDecimal("8.00");
    private static final BigDecimal MAX = BigDecimal.valueOf(20);

    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final AcademicFieldRepository fieldRepository;
    private final DomainRepository domainRepository;
    private final UniversityUnitRepository ueRepository;
    private final CourseUnitRepository courseUnitRepository;
    private final UeGradeRepository ueGradeRepository;
    private final EcGradeRepository ecGradeRepository;
    private final UeEnrollmentRepository ueEnrollmentRepository;
    private final LmdEnrollmentRepository enrollmentRepository;
    private final LmdDeliberationRepository deliberationRepository;
    private final UniversityScheduleRepository universityScheduleRepository;
    private final UniversityGroupRepository groupRepository;
    private final SemesterRepository semesterRepository;
    private final ProgramRepository programRepository;
    private final EnrollmentHistoryRepository enrollmentHistoryRepository;
    private final EcEvaluationRepository ecEvaluationRepository;
    private final AcademicRuleRepository academicRuleRepository;
    private final UniversityAttendanceRepository universityAttendanceRepository;
    private final StudentService studentService;
    private final AuditService auditService;

    /**
     * Secret de signature HMAC des jetons d'attestation universitaire (D4) :
     * on réutilise le secret JWT afin de ne pas exposer de clé supplémentaire.
     * Injecté en lecture seule au démarrage (champ non-final car @Value).
     */
    @Value("${app.jwt.secret:}")
    private String attestationSecret;

    /** Durée de validité d'un jeton d'attestation (1 an). */
    private static final long ATTESTATION_TOKEN_MS = 365L * 24 * 3600 * 1000;

    // ---------- Validation ----------

    private static void validateSemester(String semester) {
        if (semester == null || !semester.matches("S([1-9]|1[0-2])")) {
            throw new BusinessException("Semestre invalide (attendu : S1 à S12)");
        }
    }

    // ---------- Facultés ----------

    @Transactional(readOnly = true)
    public List<Faculty> listFaculties() {
        return facultyRepository.findAll();
    }

    @Transactional
    public Faculty saveFaculty(Faculty faculty, HttpServletRequest httpRequest) {
        if (faculty.getName() == null || faculty.getName().isBlank()) {
            throw new BusinessException("Le nom de la faculté est obligatoire");
        }
        if (faculty.getCode() == null || faculty.getCode().isBlank()) {
            throw new BusinessException("Le code de la faculté est obligatoire");
        }
        if (facultyRepository.existsByCode(faculty.getCode())) {
            throw new BusinessException("Une faculté porte déjà ce code : " + faculty.getCode());
        }
        Faculty saved = facultyRepository.save(faculty);
        auditService.log("CREATE", "Faculty", saved.getId(), "Création faculté " + saved.getName(), httpRequest);
        return saved;
    }

    @Transactional
    public void deleteFaculty(Long id, HttpServletRequest httpRequest) {
        Faculty faculty = facultyRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Faculté", id));
        if (!departmentRepository.findByFacultyIdOrderByName(id).isEmpty()) {
            throw new BusinessException("Supprimez d'abord les départements de cette faculté");
        }
        auditService.log("DELETE", "Faculty", id, "Suppression faculté " + faculty.getName(), httpRequest);
        facultyRepository.delete(faculty);
    }

    // ---------- Départements ----------

    @Transactional(readOnly = true)
    public List<Department> listDepartments(Long facultyId) {
        return facultyId != null
                ? departmentRepository.findByFacultyIdOrderByName(facultyId)
                : departmentRepository.findAll();
    }

    @Transactional
    public Department saveDepartment(Department department, HttpServletRequest httpRequest) {
        if (department.getFaculty() == null || department.getFaculty().getId() == null) {
            throw new BusinessException("La faculté est obligatoire");
        }
        if (department.getName() == null || department.getName().isBlank()) {
            throw new BusinessException("Le nom du département est obligatoire");
        }
        if (department.getCode() == null || department.getCode().isBlank()) {
            throw new BusinessException("Le code du département est obligatoire");
        }
        Faculty faculty = facultyRepository.findById(department.getFaculty().getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Faculté", department.getFaculty().getId()));
        department.setFaculty(faculty);
        if (departmentRepository.existsByNameAndFacultyId(department.getName(), faculty.getId())) {
            throw new BusinessException("Un département porte déjà ce nom dans cette faculté");
        }
        Department saved = departmentRepository.save(department);
        auditService.log("CREATE", "Department", saved.getId(), "Création département " + saved.getName(), httpRequest);
        return saved;
    }

    @Transactional
    public void deleteDepartment(Long id, HttpServletRequest httpRequest) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Département", id));
        if (!fieldRepository.findByDepartmentIdOrderByName(id).isEmpty()) {
            throw new BusinessException("Supprimez d'abord les filières de ce département");
        }
        auditService.log("DELETE", "Department", id, "Suppression département " + department.getName(), httpRequest);
        departmentRepository.delete(department);
    }

    // ---------- Filières ----------

    @Transactional(readOnly = true)
    public List<AcademicField> listFields(Long departmentId) {
        return departmentId != null
                ? fieldRepository.findByDepartmentIdOrderByName(departmentId)
                : fieldRepository.findAll();
    }

    // ---------- Domaines ----------

    @Transactional(readOnly = true)
    public List<Domain> listDomains() {
        return domainRepository.findAll(org.springframework.data.domain.Sort.by("name"));
    }

    @Transactional
    public Domain saveDomain(Domain domain, HttpServletRequest httpRequest) {
        if (domain.getName() == null || domain.getName().isBlank()) {
            throw new BusinessException("Le nom du domaine est obligatoire");
        }
        if (domain.getCode() == null || domain.getCode().isBlank()) {
            throw new BusinessException("Le code du domaine est obligatoire");
        }
        if (domainRepository.existsByCode(domain.getCode())) {
            throw new BusinessException("Un domaine porte déjà ce code : " + domain.getCode());
        }
        Domain saved = domainRepository.save(domain);
        auditService.log("CREATE", "Domain", saved.getId(), "Création domaine " + saved.getName(), httpRequest);
        return saved;
    }

    @Transactional
    public void deleteDomain(Long id, HttpServletRequest httpRequest) {
        Domain domain = domainRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Domaine", id));
        if (fieldRepository.findAll().stream().anyMatch(f -> f.getDomain() != null && f.getDomain().getId().equals(id))) {
            throw new BusinessException("Supprimez d'abord les filières rattachées à ce domaine");
        }
        auditService.log("DELETE", "Domain", id, "Suppression domaine " + domain.getName(), httpRequest);
        domainRepository.delete(domain);
    }

    @Transactional
    public AcademicField saveField(AcademicField field, HttpServletRequest httpRequest) {
        if (fieldRepository.existsByCode(field.getCode())) {
            throw new BusinessException("Une filière porte déjà ce code : " + field.getCode());
        }
        Department department = departmentRepository.findById(field.getDepartment().getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Département", field.getDepartment().getId()));
        field.setDepartment(department);
        if (field.getDomain() != null && field.getDomain().getId() != null) {
            Domain domain = domainRepository.findById(field.getDomain().getId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Domaine", field.getDomain().getId()));
            field.setDomain(domain);
        }
        AcademicField saved = fieldRepository.save(field);
        auditService.log("CREATE", "AcademicField", saved.getId(), "Création filière " + saved.getName(), httpRequest);
        return saved;
    }

    @Transactional
    public void deleteField(Long id, HttpServletRequest httpRequest) {
        AcademicField field = fieldRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Filière", id));
        if (!ueRepository.findByFieldIdOrderBySemester(id).isEmpty()) {
            throw new BusinessException("Supprimez d'abord les UE de cette filière");
        }
        if (!enrollmentRepository.findByFieldIdAndActiveTrueOrderById(id).isEmpty()) {
            throw new BusinessException("Des élèves sont inscrits dans cette filière");
        }
        auditService.log("DELETE", "AcademicField", id, "Suppression filière " + field.getName(), httpRequest);
        fieldRepository.delete(field);
    }

    // ---------- Unités d'enseignement ----------

    @Transactional(readOnly = true)
    public List<UniversityUnit> listUes(Long fieldId, String semester) {
        return semester != null
                ? ueRepository.findByFieldIdAndSemesterOrderByCode(fieldId, semester)
                : ueRepository.findByFieldIdOrderBySemester(fieldId);
    }

    @Transactional
    public UniversityUnit saveUe(UniversityUnit ue, HttpServletRequest httpRequest) {
        if (ue.getCode() == null || ue.getCode().isBlank()) {
            throw new BusinessException("Le code de l'UE est obligatoire");
        }
        validateSemester(ue.getSemester());
        if (ue.getCoefficient() <= 0) {
            throw new BusinessException("Le coefficient doit être positif");
        }
        if (ue.getCredits() < 0) {
            throw new BusinessException("Les crédits ECTS ne peuvent pas être négatifs");
        }
        if (ueRepository.existsByCode(ue.getCode())) {
            throw new BusinessException("Une UE porte déjà ce code : " + ue.getCode());
        }
        if (ue.getField() == null || ue.getField().getId() == null) {
            throw new BusinessException("La filière est obligatoire");
        }
        AcademicField field = fieldRepository.findById(ue.getField().getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Filière", ue.getField().getId()));
        ue.setField(field);
        UniversityUnit saved = ueRepository.save(ue);
        auditService.log("CREATE", "UniversityUnit", saved.getId(), "Création UE " + saved.getCode(), httpRequest);
        return saved;
    }

    @Transactional
    public void deleteUe(Long id, HttpServletRequest httpRequest) {
        UniversityUnit ue = ueRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("UE", id));
        if (ueGradeRepository.existsByUeId(id)) {
            throw new BusinessException("Des notes existent pour cette UE, suppression impossible");
        }
        auditService.log("DELETE", "UniversityUnit", id, "Suppression UE " + ue.getCode(), httpRequest);
        ueRepository.delete(ue);
    }

    // ---------- Inscriptions ----------

    @Transactional
    public LmdEnrollment enroll(LmdEnrollmentRequest request, HttpServletRequest httpRequest) {
        validateSemester(request.getCurrentSemester());
        Student student = studentService.findById(request.getStudentId());
        AcademicField field = fieldRepository.findById(request.getFieldId())
                .orElseThrow(() -> ResourceNotFoundException.of("Filière", request.getFieldId()));

        var existing = enrollmentRepository.findByStudentIdAndFieldId(request.getStudentId(), request.getFieldId());
        if (existing.isPresent()) {
            LmdEnrollment enrollment = existing.get();
            if (enrollment.isActive()) {
                throw new BusinessException("Cet élève est déjà inscrit dans cette filière");
            }
            enrollment.setActive(true);
            enrollment.setEnrollmentStatus(request.getEnrollmentStatus() != null
                    ? request.getEnrollmentStatus() : EnrollmentStatus.INSCRIT);
            enrollment.setCurrentSemester(request.getCurrentSemester());
            enrollment.setLevel(request.getLevel());
            enrollment.setAcademicYear(request.getAcademicYear());
            if (request.getProgramId() != null) {
                Program program = programRepository.findById(request.getProgramId())
                        .orElseThrow(() -> ResourceNotFoundException.of("Programme", request.getProgramId()));
                enrollment.setProgram(program);
            }
            LmdEnrollment saved = enrollmentRepository.save(enrollment);
            auditService.log("REENROLL", "LmdEnrollment", saved.getId(),
                    "Réinscription LMD de " + student.getFullName() + " en " + field.getName(), httpRequest);
            return saved;
        }

        LmdEnrollment.LmdEnrollmentBuilder builder = LmdEnrollment.builder()
                .student(student)
                .field(field)
                .currentSemester(request.getCurrentSemester())
                .level(request.getLevel())
                .academicYear(request.getAcademicYear())
                .enrollmentStatus(request.getEnrollmentStatus() != null
                        ? request.getEnrollmentStatus() : EnrollmentStatus.INSCRIT)
                .active(true);
        if (request.getProgramId() != null) {
            Program program = programRepository.findById(request.getProgramId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Programme", request.getProgramId()));
            builder.program(program);
        }
        LmdEnrollment saved = enrollmentRepository.save(builder.build());
        auditService.log("ENROLL", "LmdEnrollment", saved.getId(),
                "Inscription LMD de " + student.getFullName() + " en " + field.getName(), httpRequest);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<LmdEnrollment> enrollmentsByStudent(Long studentId) {
        return enrollmentRepository.findByStudentIdOrderByIdDesc(studentId);
    }

    @Transactional(readOnly = true)
    public List<LmdEnrollment> enrollmentsByField(Long fieldId) {
        return enrollmentRepository.findByFieldIdAndActiveTrueOrderById(fieldId);
    }

    // ---------- Statut d'inscription & historique ----------

    @Transactional
    public LmdEnrollment updateEnrollmentStatus(Long enrollmentId, EnrollmentStatus status,
                                                String reason, HttpServletRequest httpRequest) {
        LmdEnrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Inscription", enrollmentId));
        EnrollmentStatus previous = enrollment.getEnrollmentStatus();
        enrollment.setEnrollmentStatus(status);
        if (status == EnrollmentStatus.ABANDON || status == EnrollmentStatus.EXCLU) {
            enrollment.setActive(false);
        } else if (status == EnrollmentStatus.INSCRIT) {
            enrollment.setActive(true);
        }
        LmdEnrollment saved = enrollmentRepository.save(enrollment);
        String username = httpRequest.getUserPrincipal() != null
                ? httpRequest.getUserPrincipal().getName() : "system";
        enrollmentHistoryRepository.save(EnrollmentHistory.builder()
                .student(enrollment.getStudent())
                .field(enrollment.getField())
                .academicYear(enrollment.getAcademicYear())
                .enrollmentStatus(status)
                .reason(reason != null ? reason : "Changement de statut : " + previous + " → " + status)
                .recordedBy(username)
                .build());
        auditService.log("ENROLLMENT_STATUS", "LmdEnrollment", saved.getId(),
                "Statut inscription " + enrollment.getStudent().getFullName() + " : " + status, httpRequest);
        return saved;
    }

    @Transactional
    public LmdEnrollment changeLevel(Long enrollmentId, String newLevel, String newSemester,
                                     HttpServletRequest httpRequest) {
        LmdEnrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Inscription", enrollmentId));
        if (newSemester != null) {
            validateSemester(newSemester);
        }
        EnrollmentHistory history = EnrollmentHistory.builder()
                .student(enrollment.getStudent())
                .field(enrollment.getField())
                .fromLevel(enrollment.getLevel() != null ? enrollment.getLevel().name() : null)
                .toLevel(newLevel)
                .fromSemester(enrollment.getCurrentSemester())
                .toSemester(newSemester != null ? newSemester : enrollment.getCurrentSemester())
                .academicYear(enrollment.getAcademicYear())
                .reason("Changement de niveau")
                .recordedBy(httpRequest.getUserPrincipal() != null
                        ? httpRequest.getUserPrincipal().getName() : "system")
                .build();
        enrollmentHistoryRepository.save(history);
        if (newLevel != null) {
            // Une valeur invalide ne doit pas provoquer de HTTP 500 (D12) :
            // on valide l'énumération avant de convertir.
            try {
                enrollment.setLevel(UniversityLevel.valueOf(newLevel));
            } catch (IllegalArgumentException ex) {
                throw new BusinessException("Niveau universitaire invalide : " + newLevel);
            }
        }
        if (newSemester != null) {
            enrollment.setCurrentSemester(newSemester);
        }
        LmdEnrollment saved = enrollmentRepository.save(enrollment);
        auditService.log("LEVEL_CHANGE", "LmdEnrollment", saved.getId(),
                "Changement de niveau de " + enrollment.getStudent().getFullName(), httpRequest);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<EnrollmentHistory> enrollmentHistory(Long studentId) {
        return enrollmentHistoryRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
    }

    // ---------- Évaluations EC (multi-évaluations) ----------

    @Transactional(readOnly = true)
    public List<EcEvaluation> ecEvaluations(Long ecId, Long studentId) {
        return studentId != null
                ? ecEvaluationRepository.findByEcIdAndStudentIdOrderBySessionAsc(ecId, studentId)
                : ecEvaluationRepository.findByEcIdOrderBySessionAscEvaluationTypeAsc(ecId);
    }

    @Transactional
    public EcEvaluation saveEcEvaluation(EcEvaluation evaluation, HttpServletRequest httpRequest) {
        if (evaluation.getEc() == null || evaluation.getEc().getId() == null) {
            throw new BusinessException("L'EC est obligatoire");
        }
        Long ecId = evaluation.getEc().getId();
        CourseUnit ec = courseUnitRepository.findById(ecId)
                .orElseThrow(() -> ResourceNotFoundException.of("EC", ecId));
        evaluation.setEc(ec);
        if (evaluation.getStudent() == null || evaluation.getStudent().getId() == null) {
            throw new BusinessException("L'étudiant est obligatoire");
        }
        if (evaluation.getValue() == null
                || evaluation.getValue().compareTo(BigDecimal.ZERO) < 0
                || evaluation.getValue().compareTo(MAX) > 0) {
            throw new BusinessException("La note doit être comprise entre 0 et 20");
        }
        if (evaluation.getMaxValue() == null) {
            evaluation.setMaxValue(BigDecimal.valueOf(20));
        }
        // Upsert : une évaluation par (EC, étudiant, type, session)
        EvaluationType type = evaluation.getEvaluationType() != null
                ? evaluation.getEvaluationType() : EvaluationType.EXAMEN;
        // Session bornée à 1 (normale) ou 2 (rattrapage), comme saveEcGrade (D14).
        int session = evaluation.getSession();
        if (session != 1 && session != 2) {
            throw new BusinessException("Session invalide (1 = normale, 2 = rattrapage)");
        }
        Long studentId = evaluation.getStudent().getId();
        EcEvaluation result = evaluation;
        EcEvaluation existing = ecEvaluationRepository
                .findByEcIdAndStudentIdAndEvaluationTypeAndSession(ecId, studentId, type, session)
                .orElse(null);
        if (existing != null) {
            existing.setValue(evaluation.getValue());
            existing.setMaxValue(evaluation.getMaxValue());
            existing.setAppreciation(evaluation.getAppreciation());
            result = existing;
        } else {
            result.setEvaluationType(type);
            result.setSession(session);
        }
        EcEvaluation saved = ecEvaluationRepository.save(result);
        auditService.log("EC_EVALUATION", "EcEvaluation", saved.getId(),
                "Évaluation " + type + " EC " + ec.getCode() + " (" + studentId + ")", httpRequest);
        return saved;
    }

    @Transactional
    public void deleteEcEvaluation(Long id, HttpServletRequest httpRequest) {
        EcEvaluation evaluation = ecEvaluationRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Évaluation", id));
        auditService.log("DELETE", "EcEvaluation", id, "Suppression évaluation EC", httpRequest);
        ecEvaluationRepository.delete(evaluation);
    }

    // ---------- Règles académiques configurables ----------

    @Transactional(readOnly = true)
    public List<AcademicRule> listAcademicRules(String cycle) {
        return cycle != null ? academicRuleRepository.findByCycleOrCycleIsNull(cycle)
                : academicRuleRepository.findAll();
    }

    @Transactional
    public AcademicRule saveAcademicRule(AcademicRule rule, HttpServletRequest httpRequest) {
        if (rule.getRuleKey() == null || rule.getRuleKey().isBlank()) {
            throw new BusinessException("La clé de règle est obligatoire");
        }
        if (rule.getRuleValue() == null || rule.getRuleValue().isBlank()) {
            throw new BusinessException("La valeur de règle est obligatoire");
        }
        AcademicRule saved = academicRuleRepository.save(rule);
        auditService.log("CREATE", "AcademicRule", saved.getId(),
                "Règle " + saved.getRuleKey() + " = " + saved.getRuleValue(), httpRequest);
        return saved;
    }

    @Transactional
    public void deleteAcademicRule(Long id, HttpServletRequest httpRequest) {
        AcademicRule rule = academicRuleRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Règle", id));
        auditService.log("DELETE", "AcademicRule", id, "Suppression règle " + rule.getRuleKey(), httpRequest);
        academicRuleRepository.delete(rule);
    }

    // ---------- Notes UE ----------

    @Transactional
    public UeGrade saveUeGrade(UeGradeRequest request, HttpServletRequest httpRequest) {
        validateSemester(request.getSemester());
        Student student = studentService.findById(request.getStudentId());
        UniversityUnit ue = ueRepository.findById(request.getUeId())
                .orElseThrow(() -> ResourceNotFoundException.of("UE", request.getUeId()));
        if (!ue.getSemester().equals(request.getSemester())) {
            throw new BusinessException("L'UE " + ue.getCode() + " appartient au semestre " + ue.getSemester());
        }
        if (request.getValue().compareTo(BigDecimal.ZERO) < 0 || request.getValue().compareTo(MAX) > 0) {
            throw new BusinessException("La note doit être comprise entre 0 et 20");
        }
        int session = request.getSession() == 2 ? 2 : 1;
        UeGrade grade = ueGradeRepository
                .findByStudentIdAndUeIdAndSemesterAndSession(student.getId(), ue.getId(), request.getSemester(), session)
                .orElseGet(() -> UeGrade.builder()
                        .student(student)
                        .ue(ue)
                        .semester(request.getSemester())
                        .session(session)
                        .build());
        grade.setValue(request.getValue());
        grade.setAppreciation(request.getAppreciation());
        UeGrade saved = ueGradeRepository.save(grade);
        auditService.log("UE_GRADE", "UeGrade", saved.getId(),
                "Note UE " + ue.getCode() + " : " + saved.getValue() + "/20 (" + student.getFullName() + ")",
                httpRequest);
        return saved;
    }

    // ---------- Calculs ----------

    /**
     * Moyenne du semestre : Σ(note UE × coefficient) / Σ(coefficients).
     */
    public BigDecimal semesterAverage(List<UniversityUnit> ues, List<UeGrade> grades) {
        Map<Long, UeGrade> byUe = grades.stream()
                .collect(Collectors.toMap(g -> g.getUe().getId(), g -> g, (a, b) -> a));
        BigDecimal weighted = BigDecimal.ZERO;
        int totalCoef = 0;
        for (UniversityUnit ue : ues) {
            UeGrade grade = byUe.get(ue.getId());
            if (grade != null) {
                weighted = weighted.add(grade.getValue().multiply(BigDecimal.valueOf(ue.getCoefficient())));
                totalCoef += ue.getCoefficient();
            }
        }
        if (totalCoef == 0) {
            return null;
        }
        return weighted.divide(BigDecimal.valueOf(totalCoef), 2, RoundingMode.HALF_UP);
    }

    /**
     * Données d'un semestre chargées en un nombre de requêtes CONSTANT (au lieu
     * d'une requête par étudiant × UE × EC). Permet de calculer les résultats
     * de toute une filière en mémoire.
     *
     * @param ues        UE du semestre, triées par code
     * @param ecsByUe    EC de chaque UE (clé = id UE)
     * @param ueGrades   notes UE, clé = {@code etudiant|ue|session}
     * @param ecGrades   notes EC, clé = {@code etudiant|ec|session}
     * @param enrolledUes UE inscriptions, clé = {@code etudiant|ue}
     */
    private record SemesterGrades(
            List<UniversityUnit> ues,
            Map<Long, List<CourseUnit>> ecsByUe,
            Map<String, BigDecimal> ueGrades,
            Map<String, BigDecimal> ecGrades,
            Set<String> enrolledUes) {
    }

    private static String gradeKey(Long studentId, Long unitId, int session) {
        return studentId + "|" + unitId + "|" + session;
    }

    private static String uePairKey(Long studentId, Long ueId) {
        return studentId + "|" + ueId;
    }

    /**
     * Note effective d'une UE pour une session donnée, calculée à partir des
     * données préchargées (aucune requête unitaire).
     *
     * <ul>
     *   <li>session 1 (normale) : uniquement les notes de session 1 ;</li>
     *   <li>session 2 (rattrapage) : meilleure note entre les sessions 1 et 2.</li>
     * </ul>
     */
    private BigDecimal effectiveUeNote(Long studentId, UniversityUnit ue, int session, SemesterGrades data) {
        if (session < 2) {
            return ueNoteOf(studentId, ue, 1, data);
        }
        BigDecimal s1 = ueNoteOf(studentId, ue, 1, data);
        BigDecimal s2 = ueNoteOf(studentId, ue, 2, data);
        if (s1 == null) return s2;
        if (s2 == null) return s1;
        return s1.compareTo(s2) >= 0 ? s1 : s2;
    }

    /** Note d'une UE : moyenne pondérée des EC si la UE en a, sinon note UE directe. */
    private BigDecimal ueNoteOf(Long studentId, UniversityUnit ue, int session, SemesterGrades data) {
        List<CourseUnit> ecs = data.ecsByUe().getOrDefault(ue.getId(), List.of());
        if (ecs.isEmpty()) {
            return data.ueGrades().get(gradeKey(studentId, ue.getId(), session));
        }
        BigDecimal weighted = BigDecimal.ZERO;
        int totalCoef = 0;
        for (CourseUnit ec : ecs) {
            BigDecimal value = data.ecGrades().get(gradeKey(studentId, ec.getId(), session));
            if (value != null) {
                weighted = weighted.add(value.multiply(BigDecimal.valueOf(ec.getCoefficient())));
                totalCoef += ec.getCoefficient();
            }
        }
        if (totalCoef == 0) {
            return null;
        }
        return weighted.divide(BigDecimal.valueOf(totalCoef), 2, RoundingMode.HALF_UP);
    }

    /**
     * Charge en 5 requêtes toutes les notes d'un semestre pour TOUS les
     * étudiants d'une filière (délibération).
     */
    private SemesterGrades loadSemesterGrades(Long fieldId, String semester) {
        List<UniversityUnit> ues = ueRepository.findByFieldIdAndSemesterOrderByCode(fieldId, semester);
        return loadSemesterGrades(fieldId, semester, ues, null);
    }

    /** Charge en 5 requêtes les notes d'un semestre pour un seul étudiant. */
    private SemesterGrades loadSemesterGradesForStudent(Long fieldId, String semester, Long studentId) {
        List<UniversityUnit> ues = ueRepository.findByFieldIdAndSemesterOrderByCode(fieldId, semester);
        return loadSemesterGrades(fieldId, semester, ues, studentId);
    }

    /**
     * Chargement en lot : 5 requêtes indépendantes du nombre d'étudiants
     * (contre 2 × UE × EC × étudiants auparavant).
     *
     * @param singleStudentId si non nul, seules les notes de cet étudiant sont indexées
     */
    private SemesterGrades loadSemesterGrades(Long fieldId, String semester,
                                              List<UniversityUnit> ues, Long singleStudentId) {
        List<Long> ueIds = ues.stream().map(UniversityUnit::getId).toList();

        Map<Long, List<CourseUnit>> ecsByUe = ueIds.isEmpty() ? Map.of()
                : courseUnitRepository.findByUeIdInOrderByUeIdAscCodeAsc(ueIds).stream()
                        .collect(Collectors.groupingBy(ec -> ec.getUe().getId()));

        Map<String, BigDecimal> ueGrades = new HashMap<>();
        List<UeGrade> grades = singleStudentId != null
                ? ueGradeRepository.findByStudentIdAndSemester(singleStudentId, semester)
                : ueGradeRepository.findByUeFieldIdAndSemester(fieldId, semester);
        for (UeGrade grade : grades) {
            Long ownerId = singleStudentId != null
                    ? singleStudentId : grade.getStudent().getId();
            ueGrades.put(gradeKey(ownerId, grade.getUe().getId(), grade.getSession()), grade.getValue());
        }

        Map<String, BigDecimal> ecGrades = new HashMap<>();
        List<EcGrade> ecGradeList = singleStudentId != null
                ? ecGradeRepository.findByStudentIdAndCourseUnitUeFieldId(singleStudentId, fieldId)
                : ecGradeRepository.findByCourseUnitUeFieldId(fieldId);
        for (EcGrade grade : ecGradeList) {
            Long ownerId = singleStudentId != null
                    ? singleStudentId : grade.getStudent().getId();
            ecGrades.put(gradeKey(ownerId, grade.getCourseUnit().getId(), grade.getSession()), grade.getValue());
        }

        Set<String> enrolled = ueIds.isEmpty() ? Set.of()
                : ueEnrollmentRepository.findByUeIdIn(ueIds).stream()
                        .filter(e -> singleStudentId == null || singleStudentId.equals(e.getStudent().getId()))
                        .map(e -> uePairKey(e.getStudent().getId(), e.getUe().getId()))
                        .collect(Collectors.toSet());

        return new SemesterGrades(ues, ecsByUe, ueGrades, ecGrades, enrolled);
    }

    /**
     * Mention selon la moyenne générale (seuils standard, extensibles).
     */
    public Mention computeMention(BigDecimal average) {
        if (average == null) return null;
        if (average.compareTo(new BigDecimal("18.00")) >= 0) return Mention.EXCELLENT;
        if (average.compareTo(new BigDecimal("16.00")) >= 0) return Mention.TRES_BIEN;
        if (average.compareTo(new BigDecimal("14.00")) >= 0) return Mention.BIEN;
        if (average.compareTo(new BigDecimal("12.00")) >= 0) return Mention.ASSEZ_BIEN;
        if (average.compareTo(TEN) >= 0) return Mention.PASSABLE;
        return null;
    }

    /**
     * Calcule le résultat d'un semestre pour un étudiant : moyenne, crédits
     * obtenus/échoués, UE à repasser, mention, compensation et dettes.
     */
    @Transactional(readOnly = true)
    public LmdDeliberationResult computeResult(Long studentId, Long fieldId, String semester) {
        return computeResult(studentId, fieldId, semester, 1);
    }

    @Transactional(readOnly = true)
    public LmdDeliberationResult computeResult(Long studentId, Long fieldId, String semester, int session) {
        validateSemester(semester);
        Student student = studentService.findById(studentId);
        // Chargement en lot (5 requêtes) au lieu d'une requête par UE / EC.
        SemesterGrades data = loadSemesterGradesForStudent(fieldId, semester, studentId);
        if (data.ues().isEmpty()) {
            throw new BusinessException("Aucune UE définie pour ce semestre dans cette filière");
        }
        return computeFromSemester(student, fieldId, semester, session, data);
    }

    /**
     * Calcul pur du résultat d'un semestre à partir de données déjà chargées :
     * aucune requête SQL, donc utilisable pour toute une filière (délibération).
     */
    private LmdDeliberationResult computeFromSemester(Student student, Long fieldId, String semester,
                                                      int session, SemesterGrades data) {
        BigDecimal weighted = BigDecimal.ZERO;
        int totalCoef = 0;
        int creditsObtained = 0;
        int creditsFailed = 0;
        int totalCredits = 0;
        List<String> uesToRetake = new ArrayList<>();
        List<String> compensatedUes = new ArrayList<>();
        boolean hasMissing = false;
        boolean hasEliminatory = false;

        for (UniversityUnit ue : data.ues()) {
            if (ue.isOptionalUe() && !data.enrolledUes().contains(uePairKey(student.getId(), ue.getId()))) {
                continue; // UE optionnelle non choisie : ignorée (ni crédits, ni moyenne)
            }
            totalCredits += ue.getCredits();
            BigDecimal note = effectiveUeNote(student.getId(), ue, session, data);
            if (note == null) {
                hasMissing = true;
                creditsFailed += ue.getCredits();
                uesToRetake.add(ue.getCode());
            } else if (note.compareTo(TEN) >= 0) {
                creditsObtained += ue.getCredits();
            } else if (note.compareTo(EIGHT) >= 0) {
                // Éliminatoire potentielle : crédits non acquis, compétable si moyenne ≥ 10
                creditsFailed += ue.getCredits();
                uesToRetake.add(ue.getCode());
                compensatedUes.add(ue.getCode());
            } else {
                hasEliminatory = true;
                creditsFailed += ue.getCredits();
                uesToRetake.add(ue.getCode());
            }
            if (note != null) {
                weighted = weighted.add(note.multiply(BigDecimal.valueOf(ue.getCoefficient())));
                totalCoef += ue.getCoefficient();
            }
        }

        BigDecimal average = totalCoef == 0 ? null
                : weighted.divide(BigDecimal.valueOf(totalCoef), 2, RoundingMode.HALF_UP);

        // Compensation : moyenne ≥ 10 → les UE 8-10 non éliminatoires sont compensées (validées)
        boolean compensated = false;
        if (average != null && average.compareTo(TEN) >= 0 && !hasEliminatory && !hasMissing
                && !compensatedUes.isEmpty()) {
            creditsObtained += creditsFailed;
            creditsFailed = 0;
            compensated = true;
        }

        LmdDecision decision;
        if (average == null) {
            decision = LmdDecision.REDOUBLE;
        } else if (average.compareTo(TEN) >= 0 && !hasEliminatory && !hasMissing) {
            decision = compensated ? LmdDecision.PASSAGE_AVEC_DETTES : LmdDecision.ADMIS;
        } else if (average.compareTo(EIGHT) >= 0) {
            decision = LmdDecision.AJOURNE;
        } else {
            decision = LmdDecision.REDOUBLE;
        }

        return LmdDeliberationResult.builder()
                .studentId(student.getId())
                .matricule(student.getMatricule())
                .studentName(student.getFullName())
                .fieldId(fieldId)
                .semester(semester)
                .average(average)
                .decision(decision)
                .mention(computeMention(average))
                .creditsObtained(creditsObtained)
                .creditsFailed(creditsFailed)
                // Seules les UE réellement suivies (optionnelles choisies incluses) sont comptées :
                // les UE optionnelles non choisies ne gonflent plus le total (D13).
                .totalCredits(totalCredits)
                .uesToRetake(compensated ? new ArrayList<>() : uesToRetake)
                .compensatedUes(compensated ? compensatedUes : new ArrayList<>())
                .build();
    }

    /**
     * Délibération d'une filière pour un semestre : calcule tous les résultats,
     * classe les étudiants et enregistre les délibérations.
     */
    @Transactional
    public List<LmdDeliberationResult> deliberate(Long fieldId, String semester, HttpServletRequest httpRequest) {
        return deliberate(fieldId, semester, 1, httpRequest);
    }

    @Transactional
    public List<LmdDeliberationResult> deliberate(Long fieldId, String semester, int session,
                                                  HttpServletRequest httpRequest) {
        validateSemester(semester);
        AcademicField field = fieldRepository.findById(fieldId)
                .orElseThrow(() -> ResourceNotFoundException.of("Filière", fieldId));
        // Un étudiant est délibéré pour le SEMESTRE qu'il est en train de suivre :
        // ceux déjà avancés (ex. S4) ne sont plus délibérés pour S1 (D2).
        List<LmdEnrollment> enrollments =
                enrollmentRepository.findByFieldIdAndActiveTrueAndCurrentSemesterOrderById(fieldId, semester);
        if (enrollments.isEmpty()) {
            throw new BusinessException("Aucun étudiant inscrit au semestre " + semester
                    + " dans cette filière (délibérations non modifiées)");
        }

        // Chargement en LOT : 6 requêtes au total pour toute la filière, au lieu
        // de ~2 × UE × EC requêtes par étudiant (D11).
        SemesterGrades data = loadSemesterGrades(fieldId, semester);
        if (data.ues().isEmpty()) {
            throw new BusinessException("Aucune UE définie pour ce semestre dans cette filière");
        }

        List<LmdDeliberationResult> results = new ArrayList<>(enrollments.size());
        Map<Long, Student> studentsById = new HashMap<>();
        for (LmdEnrollment enrollment : enrollments) {
            Student student = enrollment.getStudent();
            studentsById.put(student.getId(), student);
            results.add(computeFromSemester(student, fieldId, semester, session, data));
        }
        results.sort(Comparator.comparing(LmdDeliberationResult::getAverage,
                Comparator.nullsLast(Comparator.reverseOrder())));

        // Délibérations existantes chargées en une requête (au lieu d'une par étudiant).
        Map<String, LmdDeliberation> existingByKey = new HashMap<>();
        for (LmdDeliberation d : deliberationRepository.findByFieldIdAndSemesterAndSession(fieldId, semester, session)) {
            existingByKey.put(d.getStudent().getId() + "|" + d.getSession(), d);
        }

        int rank = 0;
        BigDecimal previous = null;
        for (LmdDeliberationResult result : results) {
            rank = (previous == null || !previous.equals(result.getAverage())) ? rank + 1 : rank;
            previous = result.getAverage();
            String key = result.getStudentId() + "|" + session;
            LmdDeliberation deliberation = existingByKey.get(key);
            if (deliberation == null) {
                deliberation = LmdDeliberation.builder()
                        .student(studentsById.get(result.getStudentId()))
                        .field(field)
                        .semester(semester)
                        .session(session)
                        .build();
            }
            if (deliberation.isLocked()) {
                throw new BusinessException("Délibération verrouillée : déverrouillez avant de recalculer (filière "
                        + field.getName() + ", semestre " + semester + ")");
            }
            deliberation.setAverage(result.getAverage());
            deliberation.setDecision(result.getDecision());
            deliberation.setMention(result.getMention());
            deliberation.setCreditsObtained(result.getCreditsObtained());
            deliberation.setCreditsFailed(result.getCreditsFailed());
            deliberation.setUesToRetake(String.join(", ", result.getUesToRetake()));
            deliberation.setRankInClass(rank);
            deliberation.setDeliberatedAt(LocalDateTime.now());
            deliberationRepository.save(deliberation);
        }

        auditService.log("LMD_DELIBERATE", "LmdDeliberation", fieldId,
                "Délibération LMD " + semester + " (session " + session + ") - filière " + field.getName()
                        + " (" + results.size() + " étudiants)", httpRequest);
        return results;
    }

    @Transactional(readOnly = true)
    public List<LmdDeliberationResult> listDeliberations(Long fieldId, String semester) {
        return deliberationRepository.findByFieldIdAndSemesterOrderByAverageDesc(fieldId, semester).stream()
                .map(LmdDeliberationResult::from).toList();
    }

    /**
     * Verrouille ou déverrouille les délibérations d'une filière/semestre.
     */
    @Transactional
    public long lockDeliberations(Long fieldId, String semester, boolean locked, HttpServletRequest httpRequest) {
        List<LmdDeliberation> deliberations =
                deliberationRepository.findByFieldIdAndSemesterOrderByAverageDesc(fieldId, semester);
        deliberations.forEach(d -> d.setLocked(locked));
        deliberationRepository.saveAll(deliberations);
        auditService.log("LMD_LOCK", "LmdDeliberation", fieldId,
                (locked ? "Verrouillage" : "Déverrouillage") + " délibérations " + semester, httpRequest);
        return deliberations.size();
    }

    // ---------- Programmes de formation ----------

    @Transactional(readOnly = true)
    public List<Program> listPrograms(Long fieldId) {
        return fieldId != null ? programRepository.findByFieldIdOrderByAcademicYearDesc(fieldId)
                : programRepository.findAll();
    }

    @Transactional
    public Program saveProgram(Program program, HttpServletRequest httpRequest) {
        if (program.getName() == null || program.getName().isBlank()) {
            throw new BusinessException("Le nom du programme est obligatoire");
        }
        if (program.getCode() == null || program.getCode().isBlank()) {
            throw new BusinessException("Le code du programme est obligatoire");
        }
        if (programRepository.existsByCode(program.getCode())) {
            throw new BusinessException("Un programme porte déjà ce code : " + program.getCode());
        }
        if (program.getField() == null || program.getField().getId() == null) {
            throw new BusinessException("La filière est obligatoire");
        }
        AcademicField field = fieldRepository.findById(program.getField().getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Filière", program.getField().getId()));
        program.setField(field);
        Program saved = programRepository.save(program);
        auditService.log("CREATE", "Program", saved.getId(), "Création programme " + saved.getName(), httpRequest);
        return saved;
    }

    @Transactional
    public void deleteProgram(Long id, HttpServletRequest httpRequest) {
        Program program = programRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Programme", id));
        auditService.log("DELETE", "Program", id, "Suppression programme " + program.getName(), httpRequest);
        programRepository.delete(program);
    }

    // ---------- Semestres ----------

    @Transactional(readOnly = true)
    public List<Semester> listSemesters(Long fieldId) {
        return semesterRepository.findByFieldIdOrderByOrderIndex(fieldId);
    }

    @Transactional
    public Semester saveSemester(Semester semester, HttpServletRequest httpRequest) {
        validateSemester(semester.getCode());
        if (semester.getLabel() == null || semester.getLabel().isBlank()) {
            throw new BusinessException("Le libellé du semestre est obligatoire");
        }
        if (semester.getField() == null || semester.getField().getId() == null) {
            throw new BusinessException("La filière est obligatoire");
        }
        if (semesterRepository.existsByFieldIdAndCode(semester.getField().getId(), semester.getCode())) {
            throw new BusinessException("Ce semestre existe déjà pour cette filière");
        }
        AcademicField field = fieldRepository.findById(semester.getField().getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Filière", semester.getField().getId()));
        semester.setField(field);
        Semester saved = semesterRepository.save(semester);
        auditService.log("CREATE", "Semester", saved.getId(), "Création semestre " + saved.getCode(), httpRequest);
        return saved;
    }

    @Transactional
    public void deleteSemester(Long id, HttpServletRequest httpRequest) {
        Semester semester = semesterRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Semestre", id));
        auditService.log("DELETE", "Semester", id, "Suppression semestre " + semester.getCode(), httpRequest);
        semesterRepository.delete(semester);
    }

    // ---------- Groupes / promotions ----------

    @Transactional(readOnly = true)
    public List<UniversityGroup> listGroups(Long fieldId) {
        return fieldId != null ? groupRepository.findByFieldId(fieldId) : groupRepository.findAll();
    }

    @Transactional
    public UniversityGroup saveGroup(UniversityGroup group, HttpServletRequest httpRequest) {
        if (group.getName() == null || group.getName().isBlank()) {
            throw new BusinessException("Le nom du groupe est obligatoire");
        }
        if (group.getField() == null || group.getField().getId() == null) {
            throw new BusinessException("La filière est obligatoire");
        }
        AcademicField field = fieldRepository.findById(group.getField().getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Filière", group.getField().getId()));
        group.setField(field);
        if (groupRepository.existsByFieldIdAndCode(field.getId(), group.getCode())) {
            throw new BusinessException("Un groupe porte déjà ce code pour cette filière");
        }
        UniversityGroup saved = groupRepository.save(group);
        auditService.log("CREATE", "UniversityGroup", saved.getId(), "Création groupe " + saved.getName(), httpRequest);
        return saved;
    }

    @Transactional
    public void deleteGroup(Long id, HttpServletRequest httpRequest) {
        UniversityGroup group = groupRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Groupe", id));
        auditService.log("DELETE", "UniversityGroup", id, "Suppression groupe " + group.getName(), httpRequest);
        groupRepository.delete(group);
    }

    // ---------- Éléments Constitutifs (EC) ----------

    @Transactional(readOnly = true)
    public List<CourseUnit> listCourseUnits(Long ueId) {
        return courseUnitRepository.findByUeIdOrderByCode(ueId);
    }

    @Transactional
    public CourseUnit saveCourseUnit(CourseUnit ec, HttpServletRequest httpRequest) {
        if (ec.getCode() == null || ec.getCode().isBlank()) {
            throw new BusinessException("Le code de l'EC est obligatoire");
        }
        if (ec.getName() == null || ec.getName().isBlank()) {
            throw new BusinessException("Le nom de l'EC est obligatoire");
        }
        UniversityUnit ue = ueRepository.findById(ec.getUe().getId())
                .orElseThrow(() -> ResourceNotFoundException.of("UE", ec.getUe().getId()));
        if (courseUnitRepository.existsByUeIdAndCode(ue.getId(), ec.getCode())) {
            throw new BusinessException("Un EC porte déjà ce code dans cette UE");
        }
        if (ec.getTeacher() != null && ec.getTeacher().getId() != null) {
            ec.setTeacher(ec.getTeacher());
        }
        ec.setUe(ue);
        CourseUnit saved = courseUnitRepository.save(ec);
        auditService.log("CREATE", "CourseUnit", saved.getId(), "Création EC " + saved.getCode(), httpRequest);
        return saved;
    }

    @Transactional
    public void deleteCourseUnit(Long id, HttpServletRequest httpRequest) {
        CourseUnit ec = courseUnitRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("EC", id));
        auditService.log("DELETE", "CourseUnit", id, "Suppression EC " + ec.getCode(), httpRequest);
        courseUnitRepository.delete(ec);
    }

    // ---------- Inscription aux UE ----------

    @Transactional(readOnly = true)
    public List<UeEnrollment> ueEnrollmentsByStudent(Long studentId) {
        return ueEnrollmentRepository.findByStudentId(studentId);
    }

    @Transactional
    public UeEnrollment enrollUe(Long studentId, Long ueId, HttpServletRequest httpRequest) {
        Student student = studentService.findById(studentId);
        UniversityUnit ue = ueRepository.findById(ueId)
                .orElseThrow(() -> ResourceNotFoundException.of("UE", ueId));
        if (ueEnrollmentRepository.existsByStudentIdAndUeId(studentId, ueId)) {
            throw new BusinessException("Cet étudiant est déjà inscrit à cette UE");
        }
        UeEnrollment saved = ueEnrollmentRepository.save(UeEnrollment.builder()
                .student(student).ue(ue).active(true).build());
        auditService.log("ENROLL_UE", "UeEnrollment", saved.getId(),
                "Inscription UE " + ue.getCode() + " (" + student.getFullName() + ")", httpRequest);
        return saved;
    }

    @Transactional
    public void unenrollUe(Long id, HttpServletRequest httpRequest) {
        ueEnrollmentRepository.deleteById(id);
        auditService.log("UNENROLL_UE", "UeEnrollment", id, "Désinscription UE", httpRequest);
    }

    // ---------- Notes EC ----------

    @Transactional
    public EcGrade saveEcGrade(Long studentId, Long courseUnitId, int session, BigDecimal value,
                               String appreciation, HttpServletRequest httpRequest) {
        if (session != 1 && session != 2) {
            throw new BusinessException("Session invalide (1 = normale, 2 = rattrapage)");
        }
        Student student = studentService.findById(studentId);
        CourseUnit ec = courseUnitRepository.findById(courseUnitId)
                .orElseThrow(() -> ResourceNotFoundException.of("EC", courseUnitId));
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(MAX) > 0) {
            throw new BusinessException("La note doit être comprise entre 0 et 20");
        }
        EcGrade grade = ecGradeRepository
                .findByStudentIdAndCourseUnitIdAndSession(studentId, courseUnitId, session)
                .orElseGet(() -> EcGrade.builder()
                        .student(student).courseUnit(ec).session(session).build());
        grade.setValue(value);
        grade.setAppreciation(appreciation);
        EcGrade saved = ecGradeRepository.save(grade);
        auditService.log("EC_GRADE", "EcGrade", saved.getId(),
                "Note EC " + ec.getCode() + " (session " + session + ") : " + value + "/20 ("
                        + student.getFullName() + ")", httpRequest);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<EcGrade> ecGradesByStudent(Long studentId, Long fieldId) {
        return fieldId != null
                ? ecGradeRepository.findByStudentIdAndCourseUnitUeFieldId(studentId, fieldId)
                : ecGradeRepository.findByStudentId(studentId);
    }

    // ---------- Documents universitaires ----------

    @Transactional(readOnly = true)
    public com.school.dto.response.LmdReleveResponse buildReleve(Long studentId, Long fieldId, String semester, int session) {
        LmdDeliberationResult result = computeResult(studentId, fieldId, semester, session);
        Student student = studentService.findById(studentId);
        AcademicField field = fieldRepository.findById(fieldId).orElse(null);
        // Enrichissement (précédemment laissé à null) : programme de formation et niveau.
        LmdEnrollment enrollment = enrollmentRepository.findByStudentIdAndFieldId(studentId, fieldId).orElse(null);
        String programName = enrollment != null && enrollment.getProgram() != null
                ? enrollment.getProgram().getName() : null;
        UniversityLevel level = enrollment != null ? enrollment.getLevel() : null;
        // Un seul chargement pour toutes les lignes du relevé (pas de requête par UE).
        SemesterGrades data = loadSemesterGradesForStudent(fieldId, semester, studentId);
        List<com.school.dto.response.LmdReleveResponse.UeLine> ueLines = data.ues().stream().map(ue -> {
            BigDecimal note = null;
            try { note = effectiveUeNote(studentId, ue, session, data); } catch (Exception ignored) {}
            return com.school.dto.response.LmdReleveResponse.UeLine.builder()
                    .code(ue.getCode()).name(ue.getName())
                    .coefficient(ue.getCoefficient()).credits(ue.getCredits()).note(note).build();
        }).toList();
        return com.school.dto.response.LmdReleveResponse.builder()
                .studentName(result.getStudentName()).matricule(result.getMatricule())
                .fieldName(field != null ? field.getName() : null)
                .programName(programName)
                .level(level)
                .academicYear(com.school.utils.CodeGenerator.currentAcademicYear())
                .semester(semester).session(session)
                .average(result.getAverage()).decision(result.getDecision())
                .mention(result.getMention())
                .creditsObtained(result.getCreditsObtained())
                .creditsFailed(result.getCreditsFailed())
                .totalCredits(result.getTotalCredits())
                .ues(ueLines).build();
    }

    @Transactional(readOnly = true)
    public com.school.dto.response.LmdAttestationResponse buildAttestation(Long studentId, Long fieldId, String semester, int session) {
        LmdDeliberationResult result = computeResult(studentId, fieldId, semester, session);
        Student student = studentService.findById(studentId);
        AcademicField field = fieldRepository.findById(fieldId).orElse(null);
        LmdEnrollment enrollment = enrollmentRepository.findByStudentIdAndFieldId(studentId, fieldId).orElse(null);
        String programName = enrollment != null && enrollment.getProgram() != null
                ? enrollment.getProgram().getName() : null;
        // D4 : le jeton est désormais signé (HMAC-SHA256 du secret d'application)
        // et porte une expiration ; il n'est plus forgeable à partir du matricule
        // et de l'horodatage. Voir verifyAttestationToken().
        String token = createAttestationToken(student.getMatricule(), semester, session);
        return com.school.dto.response.LmdAttestationResponse.builder()
                .studentName(result.getStudentName()).matricule(result.getMatricule())
                .fieldName(field != null ? field.getName() : null)
                .programName(programName).diploma(null)
                .academicYear(com.school.utils.CodeGenerator.currentAcademicYear())
                .semester(semester)
                .average(result.getAverage()).mention(result.getMention())
                .decision(result.getDecision())
                .verificationToken(token).build();
    }

    /**
     * Crée un jeton d'attestation signé : {@code payload.signature} en Base64URL.
     * Le payload contient matricule, semestre, session et une expiration (1 an) ;
     * la signature est un HMAC-SHA256 calculé avec le secret d'application. Sans
     * la connaissance du secret, il est impossible de forger un jeton valide (D4).
     */
    String createAttestationToken(String matricule, String semester, int session) {
        long expiry = System.currentTimeMillis() + ATTESTATION_TOKEN_MS;
        String payload = matricule + "|" + semester + "|" + session + "|" + expiry;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + "." + signature(payload);
    }

    /**
     * Vérifie la signature d'un jeton d'attestation et son expiration.
     * Renvoie {@code false} dès qu'un élément est invalide (jeton falsifié, expiré
     * ou au format inattendu), sans jamais lever d'exception.
     */
    public boolean verifyAttestationToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        int dot = token.indexOf('.');
        if (dot <= 0) {
            return false;
        }
        String encodedPayload = token.substring(0, dot);
        String providedSignature = token.substring(dot + 1);
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(encodedPayload),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (!java.security.MessageDigest.isEqual(signature(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                providedSignature.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            return false;
        }
        String[] parts = payload.split("\\|");
        if (parts.length != 4) {
            return false;
        }
        try {
            return Long.parseLong(parts[3]) > System.currentTimeMillis();
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    /**
     * Décodage d'un jeton d'attestation VALIDE : renvoie les informations qu'il
     * porte (matricule, semestre, session, expiration), ou {@code null} si le
     * jeton est invalide ou expiré.
     */
    public com.school.dto.response.LmdAttestationResponse.AttestationInfo decodeAttestationToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        int dot = token.indexOf('.');
        if (dot <= 0) {
            return null;
        }
        String encodedPayload = token.substring(0, dot);
        String providedSignature = token.substring(dot + 1);
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(encodedPayload),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        if (!java.security.MessageDigest.isEqual(signature(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                providedSignature.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            return null;
        }
        String[] parts = payload.split("\\|");
        if (parts.length != 4) {
            return null;
        }
        try {
            long expiry = Long.parseLong(parts[3]);
            if (expiry <= System.currentTimeMillis()) {
                return null;
            }
            return com.school.dto.response.LmdAttestationResponse.AttestationInfo.builder()
                    .matricule(parts[0])
                    .semester(parts[1])
                    .session(Integer.parseInt(parts[2]))
                    .expiresAt(java.time.Instant.ofEpochMilli(expiry))
                    .build();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Signature HMAC-SHA256 (hex) du flux d'octets UTF-8 du payload. */
    private String signature(String payload) {
        try {
            String secret = (attestationSecret == null || attestationSecret.isBlank())
                    ? "attestation-fallback-dev" // uniquement si le secret n'est pas configuré (dev)
                    : attestationSecret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(mac.doFinal());
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("HMAC-SHA256 indisponible", ex);
        }
    }

    // ---------- Emploi du temps universitaire ----------

    @Transactional(readOnly = true)
    public List<UniversitySchedule> listUniversitySchedules(String semester, Long fieldId) {
        if (fieldId != null) {
            return universityScheduleRepository.findByCourseUnitUeFieldIdOrderByDayOfWeekAscStartTimeAsc(fieldId);
        }
        return semester != null
                ? universityScheduleRepository.findBySemesterOrderByDayOfWeekAscStartTimeAsc(semester)
                : universityScheduleRepository.findAll();
    }

    @Transactional
    public UniversitySchedule saveUniversitySchedule(UniversitySchedule schedule, HttpServletRequest httpRequest) {
        if (schedule.getStartTime() != null && schedule.getEndTime() != null
                && !schedule.getEndTime().isAfter(schedule.getStartTime())) {
            throw new BusinessException("L'heure de fin doit être postérieure à l'heure de début");
        }
        // Détection de conflit : même enseignant, salle ou groupe sur le même créneau.
        // On exclut le créneau en cours d'édition (même id) et on ignore les créneaux
        // sans semestre pour éviter un NPE.
        boolean conflict = universityScheduleRepository.findAll().stream()
                .filter(s -> s.getDayOfWeek() == schedule.getDayOfWeek()
                        && s.getSemester() != null
                        && s.getSemester().equals(schedule.getSemester())
                        && !java.util.Objects.equals(s.getId(), schedule.getId()))
                .anyMatch(s -> overlaps(s, schedule)
                        && (sameTeacher(s, schedule) || sameRoom(s, schedule) || sameGroup(s, schedule)));
        if (conflict) {
            throw new BusinessException("Conflit d'emploi du temps : enseignant, salle ou groupe déjà occupé sur ce créneau");
        }
        UniversitySchedule saved = universityScheduleRepository.save(schedule);
        auditService.log("CREATE", "UniversitySchedule", saved.getId(),
                "Créneau " + saved.getSessionType() + " " + saved.getGroupName()
                        + " (jour " + saved.getDayOfWeek() + ", " + saved.getStartTime() + ")", httpRequest);
        return saved;
    }

    @Transactional
    public void deleteUniversitySchedule(Long id, HttpServletRequest httpRequest) {
        UniversitySchedule schedule = universityScheduleRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Créneau", id));
        auditService.log("DELETE", "UniversitySchedule", id, "Suppression créneau " + schedule.getSessionType(), httpRequest);
        universityScheduleRepository.delete(schedule);
    }

    private boolean overlaps(UniversitySchedule a, UniversitySchedule b) {
        return a.getStartTime().isBefore(b.getEndTime()) && b.getStartTime().isBefore(a.getEndTime());
    }

    private boolean sameTeacher(UniversitySchedule a, UniversitySchedule b) {
        return a.getTeacher() != null && b.getTeacher() != null
                && a.getTeacher().getId().equals(b.getTeacher().getId());
    }

    private boolean sameRoom(UniversitySchedule a, UniversitySchedule b) {
        return a.getRoom() != null && b.getRoom() != null
                && a.getRoom().getId().equals(b.getRoom().getId());
    }

    private boolean sameGroup(UniversitySchedule a, UniversitySchedule b) {
        return a.getGroupName() != null && b.getGroupName() != null
                && a.getGroupName().equalsIgnoreCase(b.getGroupName());
    }

    // ---------- Présences universitaires ----------

    @Transactional(readOnly = true)
    public List<UniversityAttendance> listUniversityAttendances(Long ecId, java.time.LocalDate date,
                                                                UniversitySchedule.SessionType sessionType) {
        return ecId != null
                ? universityAttendanceRepository.findByEcIdAndDateAndSessionTypeOrderByStudentId(
                        ecId, date, sessionType)
                : universityAttendanceRepository.findByEcIdOrderByDateDesc(ecId);
    }

    @Transactional(readOnly = true)
    public List<UniversityAttendance> studentUniversityAttendances(Long studentId) {
        return universityAttendanceRepository.findByStudentIdOrderByDateDesc(studentId);
    }

    @Transactional
    public UniversityAttendance saveUniversityAttendance(Long ecId, Long studentId, java.time.LocalDate date,
                                                         UniversitySchedule.SessionType sessionType,
                                                         AttendanceStatus status, String groupName,
                                                         String justification, HttpServletRequest httpRequest) {
        CourseUnit ec = courseUnitRepository.findById(ecId)
                .orElseThrow(() -> ResourceNotFoundException.of("EC", ecId));
        Student student = studentService.findById(studentId);
        UniversityAttendance attendance = universityAttendanceRepository
                .findByEcIdAndStudentIdAndDateAndSessionType(ecId, studentId, date, sessionType)
                .orElseGet(() -> UniversityAttendance.builder()
                        .ec(ec).student(student).date(date).sessionType(sessionType).build());
        attendance.setStatus(status);
        attendance.setGroupName(groupName);
        attendance.setJustification(justification);
        attendance.setRecordedBy(com.school.utils.SecurityUtils.currentUser());
        UniversityAttendance saved = universityAttendanceRepository.save(attendance);
        auditService.log("UNIVERSITY_ATTENDANCE", "UniversityAttendance", saved.getId(),
                "Présence univ. " + student.getFullName() + " (" + sessionType + " " + ec.getCode() + ")", httpRequest);
        return saved;
    }

    @Transactional
    public void deleteUniversityAttendance(Long id, HttpServletRequest httpRequest) {
        UniversityAttendance attendance = universityAttendanceRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Présence", id));
        auditService.log("DELETE", "UniversityAttendance", id, "Suppression présence universitaire", httpRequest);
        universityAttendanceRepository.delete(attendance);
    }
}
