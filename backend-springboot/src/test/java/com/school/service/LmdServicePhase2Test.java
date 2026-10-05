package com.school.service;

import com.school.entity.*;
import com.school.enums.EvaluationType;
import com.school.enums.LmdDecision;
import com.school.exception.BusinessException;
import com.school.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests des correctifs de fiabilité LMD (PHASE 2) :
 * <ul>
 *   <li><b>D2</b> : la délibération ne traite que les étudiants du semestre visé ;</li>
 *   <li><b>D3</b> : la session de délibération est réellement appliquée ;</li>
 *   <li><b>D4</b> : le jeton d'attestation est signé, non forgeable ;</li>
 *   <li><b>D12</b> : un niveau universitaire inconnu ne provoque pas de 500 ;</li>
 *   <li><b>D13</b> : le total de crédits exclut les UE optionnelles non choisies ;</li>
 *   <li><b>D14</b> : une session d'évaluation hors {1, 2} est rejetée.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class LmdServicePhase2Test {

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
        lenient().when(httpRequest.getUserPrincipal()).thenReturn(null);
        // Chargement en lot : par défaut aucune UE n'a d'EC et aucune UE optionnelle suivie.
        lenient().when(courseUnitRepository.findByUeIdInOrderByUeIdAscCodeAsc(anyList())).thenReturn(List.of());
        lenient().when(ecGradeRepository.findByStudentIdAndCourseUnitUeFieldId(anyLong(), anyLong()))
                .thenReturn(List.of());
        lenient().when(ecGradeRepository.findByCourseUnitUeFieldId(anyLong())).thenReturn(List.of());
        lenient().when(ueEnrollmentRepository.findByUeIdIn(anyList())).thenReturn(List.of());
    }

    // ------------------------------------------------------------- helpers

    private Student student(Long id) {
        return Student.builder().id(id).firstName("Koffi").lastName("Konan")
                .matricule("ETU-" + id).build();
    }

    private AcademicField field(Long id) {
        Department dept = Department.builder().id(1L).name("Informatique").code("INFO").build();
        return AcademicField.builder().id(id).name("Informatique").code("F" + id).department(dept).build();
    }

    private UniversityUnit ue(Long id, String code, int coef, int credits, String semester) {
        return UniversityUnit.builder().id(id).code(code).name(code).coefficient(coef)
                .credits(credits).semester(semester).build();
    }

    private UeGrade grade(Long ueId, String semester, int session, String value) {
        return UeGrade.builder().ue(ue(ueId, "UE" + ueId, 1, 1, semester))
                .semester(semester).session(session).value(new BigDecimal(value)).build();
    }

    /** Note UE rattachée à son étudiant : requis par le chargement en lot d'une filière. */
    private UeGrade gradeOf(Student student, Long ueId, String semester, int session, String value) {
        return UeGrade.builder().student(student).ue(ue(ueId, "UE" + ueId, 1, 1, semester))
                .semester(semester).session(session).value(new BigDecimal(value)).build();
    }

    // ------------------------------------------------------------- D2

    @Test
    @DisplayName("D2 : la délibération d'un semestre sans étudiant inscrit est refusée")
    void deliberateRejectsSemesterWithoutEnrolledStudents() {
        when(fieldRepository.findById(1L)).thenReturn(Optional.of(field(1L)));
        when(enrollmentRepository.findByFieldIdAndActiveTrueAndCurrentSemesterOrderById(1L, "S1"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.deliberate(1L, "S1", 1, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Aucun étudiant inscrit au semestre S1");
    }

    @Test
    @DisplayName("D2 : la délibération ne retient que les étudiants du semestre courant visé")
    void deliberateOnlyProcessesEnrollmentsOfRequestedSemester() {
        Student s = student(10L);
        LmdEnrollment enrollment = LmdEnrollment.builder()
                .id(5L).student(s).field(field(1L)).currentSemester("S2").active(true).build();
        when(fieldRepository.findById(1L)).thenReturn(Optional.of(field(1L)));
        // Le repository n'est interrogé QUE pour le semestre demandé (S2).
        when(enrollmentRepository.findByFieldIdAndActiveTrueAndCurrentSemesterOrderById(1L, "S2"))
                .thenReturn(List.of(enrollment));
        when(ueRepository.findByFieldIdAndSemesterOrderByCode(1L, "S2"))
                .thenReturn(List.of(ue(1L, "UE1", 1, 3, "S2")));
        // La filière est chargée d'un coup ; l'étudiant vient de l'inscription
        // (plus d'appel unitaire à studentService.findById par étudiant).
        when(ueGradeRepository.findByUeFieldIdAndSemester(1L, "S2"))
                .thenReturn(List.of(gradeOf(s, 1L, "S2", 1, "14")));
        when(deliberationRepository.findByFieldIdAndSemesterAndSession(1L, "S2", 1))
                .thenReturn(List.of());
        when(deliberationRepository.save(any(LmdDeliberation.class))).thenAnswer(inv -> inv.getArgument(0));

        var results = service.deliberate(1L, "S2", 1, httpRequest);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getStudentId()).isEqualTo(10L);
        assertThat(results.get(0).getSemester()).isEqualTo("S2");
    }

    // ------------------------------------------------------------- D11 (N+1)

    @Test
    @DisplayName("D11 : délibérer 10 étudiants coûte le MÊME nombre de requêtes que pour 1")
    void deliberateDoesNotScaleQueriesWithStudentCount() {
        AcademicField f = field(1L);
        when(fieldRepository.findById(1L)).thenReturn(Optional.of(f));

        List<LmdEnrollment> enrollments = new java.util.ArrayList<>();
        List<UeGrade> grades = new java.util.ArrayList<>();
        for (long id = 10; id < 20; id++) {
            Student s = student(id);
            enrollments.add(LmdEnrollment.builder()
                    .id(id).student(s).field(f).currentSemester("S1").active(true).build());
            grades.add(gradeOf(s, 1L, "S1", 1, "14"));
        }
        when(enrollmentRepository.findByFieldIdAndActiveTrueAndCurrentSemesterOrderById(1L, "S1"))
                .thenReturn(enrollments);
        when(ueRepository.findByFieldIdAndSemesterOrderByCode(1L, "S1"))
                .thenReturn(List.of(ue(1L, "UE1", 1, 3, "S1")));
        when(ueGradeRepository.findByUeFieldIdAndSemester(1L, "S1")).thenReturn(grades);
        when(deliberationRepository.findByFieldIdAndSemesterAndSession(1L, "S1", 1)).thenReturn(List.of());
        when(deliberationRepository.save(any(LmdDeliberation.class))).thenAnswer(inv -> inv.getArgument(0));

        var results = service.deliberate(1L, "S1", 1, httpRequest);

        assertThat(results).hasSize(10);
        // Les notes de la filière sont chargées UNE seule fois pour les 10 étudiants.
        verify(ueGradeRepository, times(1)).findByUeFieldIdAndSemester(1L, "S1");
        // Aucune requête unitaire par étudiant sur les old repositories.
        verify(ueGradeRepository, never()).findByStudentIdAndSemester(anyLong(), any());
        verify(ueGradeRepository, never())
                .findByStudentIdAndUeIdAndSemesterAndSession(anyLong(), anyLong(), any(), anyInt());
        verify(deliberationRepository, never())
                .findByStudentIdAndFieldIdAndSemesterAndSession(anyLong(), anyLong(), any(), anyInt());
        // Les UE/EC sont chargés en une requête, pas une par UE.
        verify(courseUnitRepository, times(1)).findByUeIdInOrderByUeIdAscCodeAsc(anyList());
        verify(ecGradeRepository, times(1)).findByCourseUnitUeFieldId(1L);
        verify(ueEnrollmentRepository, times(1)).findByUeIdIn(anyList());
    }

    // ------------------------------------------------------------- D3

    @Test
    @DisplayName("D3 : la session 1 n'intègre PAS la note de rattrapage, la session 2 retient la meilleure")
    void sessionIsAppliedWhenComputingResult() {
        when(ueRepository.findByFieldIdAndSemesterOrderByCode(1L, "S1"))
                .thenReturn(List.of(ue(1L, "UE1", 1, 3, "S1")));
        when(studentService.findById(10L)).thenReturn(student(10L));
        // Chargement en lot : une seule requête renvoie les notes des 2 sessions.
        when(ueGradeRepository.findByStudentIdAndSemester(10L, "S1"))
                .thenReturn(List.of(grade(1L, "S1", 1, "8"), grade(1L, "S1", 2, "14")));

        var session1 = service.computeResult(10L, 1L, "S1", 1);
        var session2 = service.computeResult(10L, 1L, "S1", 2);

        // Session 1 : seule la note normale (8) compte → AJOURNÉ.
        assertThat(session1.getAverage()).isEqualByComparingTo("8.00");
        assertThat(session1.getDecision()).isEqualTo(LmdDecision.AJOURNE);
        // Session 2 : la meilleure note (14) est retenue → ADMIS.
        assertThat(session2.getAverage()).isEqualByComparingTo("14.00");
        assertThat(session2.getDecision()).isEqualTo(LmdDecision.ADMIS);
    }

    // ------------------------------------------------------------- D4

    @Test
    @DisplayName("D4 : le jeton d'attestation est signé, décodable, et non forgeable")
    void attestationTokenIsSignedAndNotForgeable() {
        String token = service.createAttestationToken("ETU-1", "S1", 1);

        assertThat(service.verifyAttestationToken(token)).isTrue();
        var info = service.decodeAttestationToken(token);
        assertThat(info).isNotNull();
        assertThat(info.getMatricule()).isEqualTo("ETU-1");
        assertThat(info.getSemester()).isEqualTo("S1");
        assertThat(info.getSession()).isEqualTo(1);
        assertThat(info.getExpiresAt()).isNotNull();

        // Un jeton au format historique (prédictible) est refusé.
        assertThat(service.verifyAttestationToken("ATT-ETU-1-S1-20260101000000")).isFalse();
        // Payload modifié sans resigner → signature invalide.
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("ETU-999|S1|1|99999999999999".getBytes(StandardCharsets.UTF_8));
        String forged = forgedPayload + "." + token.substring(token.indexOf('.') + 1);
        assertThat(service.verifyAttestationToken(forged)).isFalse();
        assertThat(service.decodeAttestationToken(forged)).isNull();
        // Signature altérée.
        assertThat(service.verifyAttestationToken(token + "00")).isFalse();
        // Entrées invalides.
        assertThat(service.verifyAttestationToken("garbage")).isFalse();
        assertThat(service.verifyAttestationToken(null)).isFalse();
        assertThat(service.verifyAttestationToken("")).isFalse();
    }

    // ------------------------------------------------------------- D12

    @Test
    @DisplayName("D12 : un niveau universitaire inconnu est rejeté (400, pas 500)")
    void changeLevelRejectsUnknownLevel() {
        LmdEnrollment enrollment = LmdEnrollment.builder()
                .id(5L).student(student(10L)).field(field(1L))
                .currentSemester("S2").active(true).build();
        when(enrollmentRepository.findById(5L)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> service.changeLevel(5L, "L9", null, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Niveau universitaire invalide");
    }

    // ------------------------------------------------------------- D13

    @Test
    @DisplayName("D13 : le total de crédits exclut les UE optionnelles non choisies")
    void totalCreditsExcludesUnselectedOptionalUe() {
        UniversityUnit obligatoire = ue(1L, "UE1", 1, 3, "S1");
        UniversityUnit optionnelle = UniversityUnit.builder()
                .id(2L).code("UE2").name("UE2").coefficient(1).credits(4)
                .semester("S1").optionalUe(true).build();
        when(ueRepository.findByFieldIdAndSemesterOrderByCode(1L, "S1"))
                .thenReturn(List.of(obligatoire, optionnelle));
        when(studentService.findById(10L)).thenReturn(student(10L));
        // L'UE optionnelle n'est PAS choisie : le lot ne contient aucune inscription UE.
        when(ueGradeRepository.findByStudentIdAndSemester(10L, "S1"))
                .thenReturn(List.of(grade(1L, "S1", 1, "14")));

        var result = service.computeResult(10L, 1L, "S1", 1);

        // Seule l'UE obligatoire (3 crédits) est comptabilisée, pas l'optionnelle (4).
        assertThat(result.getTotalCredits()).isEqualTo(3);
    }

    // ------------------------------------------------------------- D14

    @Test
    @DisplayName("D14 : une session d'évaluation hors {1, 2} est rejetée")
    void saveEcEvaluationRejectsInvalidSession() {
        CourseUnit ec = CourseUnit.builder().id(1L).code("ALGO1").name("Algorithmique 1").build();
        when(courseUnitRepository.findById(1L)).thenReturn(Optional.of(ec));
        EcEvaluation evaluation = EcEvaluation.builder()
                .ec(ec).student(student(10L)).evaluationType(EvaluationType.CC)
                .value(new BigDecimal("12")).session(3).build();

        assertThatThrownBy(() -> service.saveEcEvaluation(evaluation, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Session invalide");
    }
}
