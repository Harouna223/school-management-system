package com.school.service;

import com.school.dto.request.UeGradeRequest;
import com.school.entity.*;
import com.school.enums.LmdDecision;
import com.school.exception.BusinessException;
import com.school.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests du module LMD : règles de délibération, crédits ECTS, notes hors limites.
 */
@ExtendWith(MockitoExtension.class)
class LmdServiceTest {

    @Mock
    private FacultyRepository facultyRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private AcademicFieldRepository fieldRepository;
    @Mock
    private UniversityUnitRepository ueRepository;
    @Mock
    private LmdEnrollmentRepository enrollmentRepository;
    @Mock
    private UeGradeRepository ueGradeRepository;
    @Mock
    private LmdDeliberationRepository deliberationRepository;
    @Mock
    private ProgramRepository programRepository;
    @Mock
    private SemesterRepository semesterRepository;
    @Mock
    private CourseUnitRepository courseUnitRepository;
    @Mock
    private UeEnrollmentRepository ueEnrollmentRepository;
    @Mock
    private EcGradeRepository ecGradeRepository;
    @Mock
    private UniversityScheduleRepository universityScheduleRepository;
    @Mock
    private UniversityGroupRepository groupRepository;
    @Mock
    private DomainRepository domainRepository;
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
        // Les UE n'ont pas d'EC : la note UE est utilisée directement.
        org.mockito.Mockito.lenient()
                .when(courseUnitRepository.findByUeIdOrderByCode(anyLong())).thenReturn(List.of());
    }

    private UniversityUnit ue(Long id, String code, int coef, int credits, String semester) {
        return UniversityUnit.builder().id(id).code(code).name(code).coefficient(coef)
                .credits(credits).semester(semester).build();
    }

    private void stubUeGrade(Long studentId, Long ueId, String semester, String value) {
        when(ueGradeRepository.findByStudentIdAndUeIdAndSemesterAndSession(studentId, ueId, semester, 1))
                .thenReturn(java.util.Optional.of(UeGrade.builder()
                        .ue(ue(ueId, "UE" + ueId, 1, 1, semester))
                        .semester(semester).session(1).value(new BigDecimal(value)).build()));
        // Session 2 (rattrapage) : pas de note
        when(ueGradeRepository.findByStudentIdAndUeIdAndSemesterAndSession(studentId, ueId, semester, 2))
                .thenReturn(java.util.Optional.empty());
    }

    @Test
    void admisWhenAverageAboveTenAndNoUeBelowEight() {
        when(ueRepository.findByFieldIdAndSemesterOrderByCode(1L, "S1"))
                .thenReturn(List.of(ue(1L, "UE1", 1, 3, "S1"), ue(2L, "UE2", 1, 2, "S1")));
        stubUeGrade(10L, 1L, "S1", "12");
        stubUeGrade(10L, 2L, "S1", "11");
        when(studentService.findById(10L))
                .thenReturn(Student.builder().id(10L).matricule("ETU-2026-0001").build());

        var result = service.computeResult(10L, 1L, "S1");

        assertThat(result.getDecision()).isEqualTo(LmdDecision.ADMIS);
        assertThat(result.getCreditsObtained()).isEqualTo(5);
        assertThat(result.getCreditsFailed()).isEqualTo(0);
        assertThat(result.getUesToRetake()).isEmpty();
    }

    @Test
    void ajourneWhenOneUeBelowEight() {
        when(ueRepository.findByFieldIdAndSemesterOrderByCode(1L, "S1"))
                .thenReturn(List.of(ue(1L, "UE1", 1, 3, "S1"), ue(2L, "UE2", 1, 2, "S1")));
        stubUeGrade(10L, 1L, "S1", "14");
        stubUeGrade(10L, 2L, "S1", "7.5");
        when(studentService.findById(10L))
                .thenReturn(Student.builder().id(10L).matricule("ETU-2026-0001").build());

        var result = service.computeResult(10L, 1L, "S1");

        assertThat(result.getDecision()).isEqualTo(LmdDecision.AJOURNE);
        assertThat(result.getUesToRetake()).containsExactly("UE2");
        assertThat(result.getCreditsFailed()).isEqualTo(2);
    }

    @Test
    void redoubleWhenAverageBelowEight() {
        when(ueRepository.findByFieldIdAndSemesterOrderByCode(1L, "S1"))
                .thenReturn(List.of(ue(1L, "UE1", 1, 3, "S1"), ue(2L, "UE2", 1, 2, "S1")));
        stubUeGrade(10L, 1L, "S1", "6");
        stubUeGrade(10L, 2L, "S1", "7");
        when(studentService.findById(10L))
                .thenReturn(Student.builder().id(10L).matricule("ETU-2026-0001").build());

        var result = service.computeResult(10L, 1L, "S1");

        assertThat(result.getDecision()).isEqualTo(LmdDecision.REDOUBLE);
    }

    @Test
    void missingGradeLeadsToRetakeAndNotAdmis() {
        when(ueRepository.findByFieldIdAndSemesterOrderByCode(1L, "S1"))
                .thenReturn(List.of(ue(1L, "UE1", 1, 3, "S1"), ue(2L, "UE2", 1, 2, "S1")));
        stubUeGrade(10L, 1L, "S1", "12");
        when(studentService.findById(10L))
                .thenReturn(Student.builder().id(10L).matricule("ETU-2026-0001").build());

        var result = service.computeResult(10L, 1L, "S1");

        assertThat(result.getDecision()).isNotEqualTo(LmdDecision.ADMIS);
        assertThat(result.getUesToRetake()).containsExactly("UE2");
    }

    @Test
    void passageAvecDettesWhenCompensationApplies() {
        when(ueRepository.findByFieldIdAndSemesterOrderByCode(1L, "S1"))
                .thenReturn(List.of(ue(1L, "UE1", 1, 3, "S1"), ue(2L, "UE2", 1, 2, "S1")));
        stubUeGrade(10L, 1L, "S1", "14");
        stubUeGrade(10L, 2L, "S1", "9");   // 8 ≤ note < 10 : compensable, moyenne = 11.5 ≥ 10
        when(studentService.findById(10L))
                .thenReturn(Student.builder().id(10L).matricule("ETU-2026-0001").build());

        var result = service.computeResult(10L, 1L, "S1");

        assertThat(result.getDecision()).isEqualTo(LmdDecision.PASSAGE_AVEC_DETTES);
        assertThat(result.getCreditsObtained()).isEqualTo(5);
        assertThat(result.getCreditsFailed()).isZero();
        assertThat(result.getCompensatedUes()).containsExactly("UE2");
    }

    @Test
    void invalidSemesterIsRejected() {
        UeGradeRequest request = UeGradeRequest.builder()
                .studentId(10L).ueId(1L).semester("S13").value(new BigDecimal("10")).build();
        assertThatThrownBy(() -> service.saveUeGrade(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Semestre invalide");
    }

    @Test
    void negativeGradeIsRejected() {
        when(studentService.findById(10L)).thenReturn(Student.builder().id(10L).build());
        when(ueRepository.findById(1L)).thenReturn(java.util.Optional.of(ue(1L, "UE1", 1, 3, "S1")));
        assertThatThrownBy(() -> service.saveUeGrade(UeGradeRequest.builder()
                .studentId(10L).ueId(1L).semester("S1").value(new BigDecimal("-1")).build(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("0 et 20");
    }

    @Test
    void gradeAboveTwentyIsRejected() {
        when(studentService.findById(10L)).thenReturn(Student.builder().id(10L).build());
        when(ueRepository.findById(1L)).thenReturn(java.util.Optional.of(ue(1L, "UE1", 1, 3, "S1")));
        assertThatThrownBy(() -> service.saveUeGrade(UeGradeRequest.builder()
                .studentId(10L).ueId(1L).semester("S1").value(new BigDecimal("21")).build(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("0 et 20");
    }
}
