package com.school.service;

import com.school.entity.*;
import com.school.enums.AttendanceStatus;
import com.school.enums.EnrollmentStatus;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests des nouvelles fonctionnalités LMD : domaines, groupes, statut
 * d'inscription, évaluations EC, règles académiques et présences.
 */
@ExtendWith(MockitoExtension.class)
class LmdServiceFeaturesTest {

    @Mock
    private FacultyRepository facultyRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private AcademicFieldRepository fieldRepository;
    @Mock
    private DomainRepository domainRepository;
    @Mock
    private UniversityUnitRepository ueRepository;
    @Mock
    private CourseUnitRepository courseUnitRepository;
    @Mock
    private UeGradeRepository ueGradeRepository;
    @Mock
    private EcGradeRepository ecGradeRepository;
    @Mock
    private UeEnrollmentRepository ueEnrollmentRepository;
    @Mock
    private LmdEnrollmentRepository enrollmentRepository;
    @Mock
    private LmdDeliberationRepository deliberationRepository;
    @Mock
    private UniversityScheduleRepository universityScheduleRepository;
    @Mock
    private UniversityGroupRepository groupRepository;
    @Mock
    private SemesterRepository semesterRepository;
    @Mock
    private ProgramRepository programRepository;
    @Mock
    private EnrollmentHistoryRepository enrollmentHistoryRepository;
    @Mock
    private EcEvaluationRepository ecEvaluationRepository;
    @Mock
    private AcademicRuleRepository academicRuleRepository;
    @Mock
    private UniversityAttendanceRepository universityAttendanceRepository;
    @Mock
    private StudentService studentService;
    @Mock
    private AuditService auditService;
    @Mock
    private HttpServletRequest httpRequest;

    private LmdService service;

    @BeforeEach
    void setUp() {
        service = new LmdService(facultyRepository, departmentRepository, fieldRepository,
                domainRepository, ueRepository, courseUnitRepository,
                ueGradeRepository, ecGradeRepository, ueEnrollmentRepository,
                enrollmentRepository, deliberationRepository, universityScheduleRepository,
                groupRepository, semesterRepository, programRepository,
                enrollmentHistoryRepository, ecEvaluationRepository, academicRuleRepository,
                universityAttendanceRepository,
                studentService, auditService);
        org.mockito.Mockito.lenient().when(httpRequest.getUserPrincipal()).thenReturn(null);
    }

    private Student student(Long id) {
        return Student.builder().id(id).firstName("Koffi").lastName("Konan").matricule("ETU-" + id).build();
    }

    private AcademicField field(Long id, String name) {
        Department dept = Department.builder().id(1L).name("Informatique").code("INFO").build();
        return AcademicField.builder().id(id).name(name).code("F" + id).department(dept).build();
    }

    @Test
    void saveDomainRejectsBlankName() {
        assertThatThrownBy(() -> service.saveDomain(Domain.builder().name("  ").code("SC").build(), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nom");
    }

    @Test
    void saveDomainRejectsDuplicateCode() {
        when(domainRepository.existsByCode("SC")).thenReturn(true);
        assertThatThrownBy(() -> service.saveDomain(Domain.builder().name("Sciences").code("SC").build(), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("code");
    }

    @Test
    void saveDomainSavesValidDomain() {
        when(domainRepository.save(any(Domain.class)))
                .thenAnswer(inv -> {
                    Domain d = inv.getArgument(0);
                    d.setId(10L);
                    return d;
                });
        Domain saved = service.saveDomain(Domain.builder().name("Sciences").code("SC").build(), httpRequest);
        assertThat(saved.getId()).isEqualTo(10L);
        assertThat(saved.getName()).isEqualTo("Sciences");
        verify(domainRepository).save(any(Domain.class));
    }

    @Test
    void deleteDomainRejectsWhenFieldsLinked() {
        Domain domain = Domain.builder().id(1L).name("Sciences").code("SC").build();
        when(domainRepository.findById(1L)).thenReturn(Optional.of(domain));
        when(fieldRepository.findAll()).thenReturn(List.of(
                AcademicField.builder().id(2L).domain(domain).build()));
        assertThatThrownBy(() -> service.deleteDomain(1L, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("filières");
    }

    @Test
    void saveGroupRejectsMissingField() {
        assertThatThrownBy(() -> service.saveGroup(UniversityGroup.builder()
                .name("Groupe A").code("GA").build(), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("filière");
    }

    @Test
    void saveGroupRejectsDuplicateCodePerField() {
        when(fieldRepository.findById(1L)).thenReturn(Optional.of(field(1L, "Informatique")));
        when(groupRepository.existsByFieldIdAndCode(1L, "GA")).thenReturn(true);
        assertThatThrownBy(() -> service.saveGroup(UniversityGroup.builder()
                .name("Groupe A").code("GA").field(field(1L, "Informatique")).build(), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("code");
    }

    @Test
    void updateEnrollmentStatusToAbandonDeactivatesAndWritesHistory() {
        Student student = student(10L);
        LmdEnrollment enrollment = LmdEnrollment.builder()
                .id(5L).student(student).field(field(1L, "Informatique"))
                .currentSemester("S2").active(true).enrollmentStatus(EnrollmentStatus.INSCRIT).build();
        when(enrollmentRepository.findById(5L)).thenReturn(Optional.of(enrollment));
        when(enrollmentRepository.save(any(LmdEnrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        LmdEnrollment saved = service.updateEnrollmentStatus(5L, EnrollmentStatus.ABANDON, "Démission", httpRequest);

        assertThat(saved.getEnrollmentStatus()).isEqualTo(EnrollmentStatus.ABANDON);
        assertThat(saved.isActive()).isFalse();
        verify(enrollmentHistoryRepository).save(any(EnrollmentHistory.class));
        verify(auditService).log(org.mockito.ArgumentMatchers.eq("ENROLLMENT_STATUS"), any(), any(), any(), any());
    }

    @Test
    void changeLevelUpdatesLevelAndSemester() {
        Student student = student(10L);
        LmdEnrollment enrollment = LmdEnrollment.builder()
                .id(5L).student(student).field(field(1L, "Informatique"))
                .currentSemester("S2").active(true).build();
        when(enrollmentRepository.findById(5L)).thenReturn(Optional.of(enrollment));
        when(enrollmentRepository.save(any(LmdEnrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        LmdEnrollment saved = service.changeLevel(5L, "L2", "S3", httpRequest);

        assertThat(saved.getLevel()).isEqualTo(com.school.enums.UniversityLevel.L2);
        assertThat(saved.getCurrentSemester()).isEqualTo("S3");
        verify(enrollmentHistoryRepository).save(any(EnrollmentHistory.class));
    }

    @Test
    void changeLevelRejectsInvalidSemester() {
        Student student = student(10L);
        LmdEnrollment enrollment = LmdEnrollment.builder()
                .id(5L).student(student).field(field(1L, "Informatique"))
                .currentSemester("S2").active(true).build();
        when(enrollmentRepository.findById(5L)).thenReturn(Optional.of(enrollment));
        assertThatThrownBy(() -> service.changeLevel(5L, "L2", "S99", httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Semestre invalide");
    }

    @Test
    void saveEcEvaluationRejectsOutOfRange() {
        CourseUnit ec = CourseUnit.builder().id(1L).code("ALGO1").name("Algorithmique 1").build();
        when(courseUnitRepository.findById(1L)).thenReturn(Optional.of(ec));
        EcEvaluation evaluation = EcEvaluation.builder()
                .ec(ec).student(student(10L))
                .evaluationType(com.school.enums.EvaluationType.CC)
                .value(new BigDecimal("21")).build();
        assertThatThrownBy(() -> service.saveEcEvaluation(evaluation, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("0 et 20");
    }

    @Test
    void saveEcEvaluationDefaultsMaxValueTo20() {
        CourseUnit ec = CourseUnit.builder().id(1L).code("ALGO1").name("Algorithmique 1").build();
        when(courseUnitRepository.findById(1L)).thenReturn(Optional.of(ec));
        when(ecEvaluationRepository.save(any(EcEvaluation.class))).thenAnswer(inv -> inv.getArgument(0));

        EcEvaluation saved = service.saveEcEvaluation(EcEvaluation.builder()
                .ec(ec).student(student(10L))
                .evaluationType(com.school.enums.EvaluationType.CC)
                .value(new BigDecimal("15")).build(), httpRequest);

        assertThat(saved.getMaxValue()).isEqualByComparingTo(new BigDecimal("20"));
        verify(ecEvaluationRepository).save(any(EcEvaluation.class));
    }

    @Test
    void saveAcademicRuleRejectsBlankKey() {
        assertThatThrownBy(() -> service.saveAcademicRule(AcademicRule.builder()
                .ruleKey("  ").ruleValue("10").build(), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("clé");
    }

    @Test
    void saveAcademicRuleSavesValidRule() {
        when(academicRuleRepository.save(any(AcademicRule.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        AcademicRule saved = service.saveAcademicRule(AcademicRule.builder()
                .cycle("UNIVERSITE").ruleKey("validation_threshold").ruleValue("10").build(), httpRequest);
        assertThat(saved.getRuleKey()).isEqualTo("validation_threshold");
        assertThat(saved.getRuleValue()).isEqualTo("10");
    }

    @Test
    void saveUniversityAttendanceCreatesWhenAbsent() {
        CourseUnit ec = CourseUnit.builder().id(1L).code("ALGO1").name("Algorithmique 1").build();
        Student student = student(10L);
        when(courseUnitRepository.findById(1L)).thenReturn(Optional.of(ec));
        when(studentService.findById(10L)).thenReturn(student);
        when(universityAttendanceRepository.findByEcIdAndStudentIdAndDateAndSessionType(
                1L, 10L, LocalDate.of(2026, 9, 1), UniversitySchedule.SessionType.TD))
                .thenReturn(Optional.empty());
        when(universityAttendanceRepository.save(any(UniversityAttendance.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UniversityAttendance saved = service.saveUniversityAttendance(
                1L, 10L, LocalDate.of(2026, 9, 1), UniversitySchedule.SessionType.TD,
                AttendanceStatus.PRESENT, "Groupe A", null, httpRequest);

        assertThat(saved.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(saved.getEc().getId()).isEqualTo(1L);
        assertThat(saved.getStudent().getId()).isEqualTo(10L);
    }

    @Test
    void saveUniversityAttendanceUpdatesExisting() {
        CourseUnit ec = CourseUnit.builder().id(1L).code("ALGO1").name("Algorithmique 1").build();
        Student student = student(10L);
        UniversityAttendance existing = UniversityAttendance.builder()
                .id(7L).ec(ec).student(student)
                .date(LocalDate.of(2026, 9, 1)).sessionType(UniversitySchedule.SessionType.TD)
                .status(AttendanceStatus.ABSENT).build();
        when(courseUnitRepository.findById(1L)).thenReturn(Optional.of(ec));
        when(studentService.findById(10L)).thenReturn(student);
        when(universityAttendanceRepository.findByEcIdAndStudentIdAndDateAndSessionType(
                1L, 10L, LocalDate.of(2026, 9, 1), UniversitySchedule.SessionType.TD))
                .thenReturn(Optional.of(existing));
        when(universityAttendanceRepository.save(any(UniversityAttendance.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UniversityAttendance saved = service.saveUniversityAttendance(
                1L, 10L, LocalDate.of(2026, 9, 1), UniversitySchedule.SessionType.TD,
                AttendanceStatus.PRESENT, "Groupe A", null, httpRequest);

        assertThat(saved.getId()).isEqualTo(7L);
        assertThat(saved.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    void deleteDomainThrowsWhenNotFound() {
        when(domainRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteDomain(99L, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
