package com.school.service;

import com.school.dto.request.GradeRequest;
import com.school.entity.Exam;
import com.school.entity.Grade;
import com.school.entity.Student;
import com.school.entity.Subject;
import com.school.enums.Term;
import com.school.exception.BusinessException;
import com.school.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests des notes : bornes 0-20 et validation des valeurs hors limites.
 */
@ExtendWith(MockitoExtension.class)
class GradeServiceTest {

    @Mock
    private GradeRepository gradeRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private BulletinRepository bulletinRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private ExamService examService;
    @Mock
    private AuditService auditService;
    @Mock
    private ReportService reportService;
    @Mock
    private WhatsAppService whatsappService;
    @Mock
    private EmailService emailService;
    @Mock
    private SmsService smsService;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private AcademicCalendarService academicCalendarService;
    @Mock
    private AppreciationScaleService appreciationScaleService;

    private GradeService service;

    @BeforeEach
    void setUp() {
        service = new GradeService(gradeRepository, studentRepository, bulletinRepository,
                notificationRepository, examService, auditService, reportService,
                whatsappService, emailService, smsService, accessControlService,
                academicCalendarService, appreciationScaleService);
        // L'année scolaire est résolue via la table academic_years : les tests qui
        // en dépendent la stubbent explicitement, les autres restent inchangés.
        org.mockito.Mockito.lenient().when(academicCalendarService.resolveYear(any(), any(), any()))
                .thenReturn(com.school.entity.AcademicYear.builder().id(1L).label("2026-2027").build());
        org.mockito.Mockito.lenient().when(academicCalendarService.requireYearFor(any()))
                .thenReturn(com.school.entity.AcademicYear.builder().id(1L).label("2026-2027").build());
        // Le calcul du bulletin a besoin du cycle de l'élève : un élève par
        // défaut suffit, les tests qui en exigent un précis le surchargent.
        org.mockito.Mockito.lenient().when(studentRepository.findById(any()))
                .thenReturn(java.util.Optional.of(eleveBulleting()));
    }

    private GradeRequest request(String value, String maxValue) {
        return GradeRequest.builder()
                .studentId(10L)
                .examId(1L)
                .value(value != null ? new BigDecimal(value) : null)
                .maxValue(maxValue != null ? new BigDecimal(maxValue) : null)
                .build();
    }

    /** Classe partagée par l'élève et l'évaluation : la saisie d'une note exige
     *  désormais que l'élève appartienne à la classe de l'évaluation. */
    private com.school.entity.SchoolClass classe(Long id) {
        return com.school.entity.SchoolClass.builder().id(id).name("6e A").code("6A").build();
    }

    private Exam evaluation() {
        return Exam.builder().id(1L).name("Évaluation").schoolClass(classe(3L))
                .subject(Subject.builder().id(5L).name("Matière").coefficient(1).build())
                .build();
    }

    private Student eleveNotable() {
        return Student.builder().id(10L).firstName("Test").lastName("Student")
                .matricule("ST-10").schoolClass(classe(3L)).build();
    }

    @Test
    void negativeGradeIsRejected() {
        when(examService.findById(1L)).thenReturn(evaluation());
        when(studentRepository.findById(10L)).thenReturn(java.util.Optional.of(eleveNotable()));

        assertThatThrownBy(() -> service.save(request("-1", null), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("négative");
    }

    @Test
    void gradeAboveTwentyIsRejected() {
        when(examService.findById(1L)).thenReturn(evaluation());
        when(studentRepository.findById(10L)).thenReturn(java.util.Optional.of(eleveNotable()));

        assertThatThrownBy(() -> service.save(request("21", null), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ne peut pas dépasser");
    }

    @Test
    void gradeAboveCustomMaxIsRejected() {
        when(examService.findById(1L)).thenReturn(evaluation());
        when(studentRepository.findById(10L)).thenReturn(java.util.Optional.of(eleveNotable()));

        assertThatThrownBy(() -> service.save(request("15", "10"), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ne peut pas dépasser");
    }

    @Test
    @DisplayName("un élève d'une AUTRE classe que l'évaluation est refusé")
    void eleveDuneAutreClasseRefuse() {
        Exam evaluation = evaluation();
        Student autreClasse = Student.builder().id(10L).firstName("Awa").lastName("Diop")
                .schoolClass(classe(9L)).build();
        when(examService.findById(1L)).thenReturn(evaluation);
        when(studentRepository.findById(10L)).thenReturn(java.util.Optional.of(autreClasse));

        assertThatThrownBy(() -> service.save(request("12", null), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("n'appartient pas à la classe de l'évaluation");
    }

    // ============================================================ moyennes normalisées
    //
    // Une note est saisie sur une échelle (maxValue) qui peut différer de 20.
    // La moyenne pondérée doit comparer des valeurs RAMENÉES sur 20, sinon une
    // note /50 ou /100 gonfle artificiellement moyennes, rangs et mentions.

    private Grade grade(BigDecimal value, String maxValue, int examCoef, int subjectCoef) {
        Exam exam = Exam.builder()
                .id(1L)
                .coefficient(examCoef)
                .subject(Subject.builder().id(5L).coefficient(subjectCoef).build())
                .build();
        return Grade.builder()
                .id(100L)
                .exam(exam)
                .value(value)
                .maxValue(new BigDecimal(maxValue))
                .build();
    }

    @Test
    @DisplayName("la moyenne ramène chaque note sur 20 quelle que soit son échelle")
    void moyenneNormaliseeEntreEchellesDifferentes() {
        // 15/20 -> 15.00  ;  40/50 -> 16.00  ; coefficients 1 et 1 => moyenne 15.50
        when(gradeRepository.findByStudentIdAndTermAndYear(10L, Term.T1, "2026-2027")).thenReturn(List.of(
                grade(new BigDecimal("15"), "20", 1, 1),
                grade(new BigDecimal("40"), "50", 1, 1)
        ));

        BigDecimal moyenne = service.averageForStudent(10L, Term.T1);

        assertThat(moyenne).isEqualByComparingTo("15.50");
    }

    @Test
    @DisplayName("la moyenne pondère par les coefficients APRES normalisation")
    void moyennePondereApresNormalisation() {
        // 10/20 (coef 2) + 18/20 (coef 1) => (10*2 + 18) / 3 = 12.67
        when(gradeRepository.findByStudentIdAndTermAndYear(10L, Term.T1, "2026-2027")).thenReturn(List.of(
                grade(new BigDecimal("10"), "20", 2, 1),
                grade(new BigDecimal("18"), "20", 1, 1)
        ));

        BigDecimal moyenne = service.averageForStudent(10L, Term.T1);

        assertThat(moyenne).isEqualByComparingTo("12.67");
    }

    @Test
    @DisplayName("aucune note => moyenne null (jamais 0)")
    void moyenneNulleSansNotes() {
        when(gradeRepository.findByStudentIdAndTermAndYear(10L, Term.T1, "2026-2027")).thenReturn(List.of());

        assertThat(service.averageForStudent(10L, Term.T1)).isNull();
    }

    @Test
    @DisplayName("seules les notes de L'ANNÉE demandée comptent (plus de mélange pluriannuel)")
    void moyenneBorneeALAnneeScolaire() {
        when(academicCalendarService.resolveYear(null, null, null))
                .thenReturn(com.school.entity.AcademicYear.builder().id(2L).label("2025-2026").build());
        when(gradeRepository.findByStudentIdAndTermAndYear(10L, Term.T1, "2025-2026"))
                .thenReturn(List.of(grade(new BigDecimal("9"), "20", 1, 1)));

        assertThat(service.averageForStudent(10L, Term.T1, null)).isEqualByComparingTo("9.00");
        org.mockito.Mockito.verify(gradeRepository)
                .findByStudentIdAndTermAndYear(10L, Term.T1, "2025-2026");
    }

    @Test
    @DisplayName("une maxValue maximale de l'échelle est conservée à la norme 20 par défaut")
    void maxValueDefautA20QuandAbsent() {
        when(gradeRepository.findByStudentIdAndTermAndYear(10L, Term.T1, "2026-2027")).thenReturn(List.of(
                grade(new BigDecimal("12"), "20", 1, 1)
        ));

        assertThat(service.averageForStudent(10L, Term.T1)).isEqualByComparingTo("12.00");
    }

    // ============================================================ resauvegarde

    @Test
    @DisplayName("ré-enregistrer une note met à jour son échelle (maxValue)")
    void resauvegardeMetAJourMaxValue() {
        when(examService.findById(1L)).thenReturn(evaluation());
        when(studentRepository.findById(10L)).thenReturn(Optional.of(eleveNotable()));
        Grade existante = Grade.builder()
                .id(100L)
                .student(eleveNotable())
                .exam(evaluation())
                .maxValue(new BigDecimal("20"))
                .build();
        when(gradeRepository.findByStudentIdAndExamId(10L, 1L)).thenReturn(Optional.of(existante));
        when(gradeRepository.save(any(Grade.class))).thenAnswer(inv -> inv.getArgument(0));

        service.save(request("8", "10"), null);

        ArgumentCaptor<Grade> captor = ArgumentCaptor.forClass(Grade.class);
        org.mockito.Mockito.verify(gradeRepository).save(captor.capture());
        assertThat(captor.getValue().getMaxValue()).isEqualByComparingTo("10");
        assertThat(captor.getValue().getValue()).isEqualByComparingTo("8");
    }

    // ============================================================ bulletin 7 colonnes

    private static Subject matiere(long id, String nom, int coefficient) {
        return Subject.builder().id(id).name(nom).code("S" + id).coefficient(coefficient).build();
    }

    private static Student eleveBulleting() {
        return Student.builder().id(1L).firstName("Test").lastName("Eleve").matricule("ST-1")
                .educationCycle(com.school.enums.EducationCycle.COLLEGE)
                .build();
    }

    private static Grade noteDe(Subject matiere, com.school.enums.ExamType type, int coefEvaluation,
                                String valeur, String bareme) {
        Exam exam = Exam.builder().id(200L).name("E").type(type)
                .coefficient(coefEvaluation).subject(matiere).build();
        return Grade.builder().id(1L).exam(exam)
                .value(new BigDecimal(valeur))
                .maxValue(bareme != null ? new BigDecimal(bareme) : null)
                .build();
    }

    @Test
    @DisplayName("bulletin : note/classe, note/compo, coefficient et appréciation")
    void bulletinCalculeLesSeptColonnes() {
        Subject maths = matiere(1L, "Mathématiques", 4);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(eleveBulleting()));
        when(appreciationScaleService.resolveForScale(any(), any(), any())).thenReturn("Assez bien");
        when(gradeRepository.findByStudentIdAndTermAndYear(1L, Term.T1, "2026-2027")).thenReturn(List.of(
                noteDe(maths, com.school.enums.ExamType.DEVOIR, 1, "12", "20"),
                noteDe(maths, com.school.enums.ExamType.CONTROLE, 1, "16", "20"),
                noteDe(maths, com.school.enums.ExamType.COMPOSITION, 1, "10", "20")));

        var lignes = service.bulletinLines(1L, Term.T1, "2026-2027");

        assertThat(lignes).hasSize(1);
        var ligne = lignes.get(0);
        assertThat(ligne.getSubjectName()).isEqualTo("Mathématiques");
        assertThat(ligne.getNoteClasse()).isEqualByComparingTo("14.00");
        assertThat(ligne.getNoteComposition()).isEqualByComparingTo("10.00");
        assertThat(ligne.getMoyenne()).isEqualByComparingTo("12.00");
        assertThat(ligne.getCoefficient()).isEqualTo(4);
        assertThat(ligne.getNoteCoef()).isEqualByComparingTo("48.00");
        assertThat(ligne.getObservation()).isEqualTo("Assez bien");
    }

    @Test
    @DisplayName("bulletin : sans composition, la moyenne retient la note de classe")
    void bulletinSansCompositionNePenalisePas() {
        Subject svt = matiere(2L, "SVT", 2);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(eleveBulleting()));
        when(appreciationScaleService.resolveForScale(any(), any(), any())).thenReturn("Bien");
        when(gradeRepository.findByStudentIdAndTermAndYear(1L, Term.T1, "2026-2027")).thenReturn(List.of(
                noteDe(svt, com.school.enums.ExamType.DEVOIR, 1, "15", "20")));

        var ligne = service.bulletinLines(1L, Term.T1, "2026-2027").get(0);

        assertThat(ligne.getNoteComposition()).isNull();
        assertThat(ligne.getMoyenne()).isEqualByComparingTo("15.00");
        assertThat(ligne.getNoteCoef()).isEqualByComparingTo("30.00");
        assertThat(ligne.getObservation()).isEqualTo("Bien");
    }

    @Test
    @DisplayName("bulletin : une note sur 10 est ramenée sur 20")
    void bulletinNormaliseLeBareme() {
        Subject physique = matiere(3L, "Physique", 1);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(eleveBulleting()));
        when(gradeRepository.findByStudentIdAndTermAndYear(1L, Term.T1, "2026-2027")).thenReturn(List.of(
                noteDe(physique, com.school.enums.ExamType.DEVOIR, 1, "8", "10")));

        assertThat(service.bulletinLines(1L, Term.T1, "2026-2027").get(0).getNoteClasse())
                .isEqualByComparingTo("16.00");
    }

    @Test
    @DisplayName("moyenne générale = Σ(note coef) / Σ(coef)")
    void moyenneGeneralePondereeParLesCoefficients() {
        Subject maths = matiere(1L, "Mathématiques", 4);
        Subject svt = matiere(2L, "SVT", 2);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(eleveBulleting()));
        when(gradeRepository.findByStudentIdAndTermAndYear(1L, Term.T1, "2026-2027")).thenReturn(List.of(
                noteDe(maths, com.school.enums.ExamType.DEVOIR, 1, "12", "20"),
                noteDe(maths, com.school.enums.ExamType.COMPOSITION, 1, "10", "20"),
                noteDe(svt, com.school.enums.ExamType.DEVOIR, 1, "15", "20")));

        // Maths : (12+10)/2 = 11 → ×4 = 44 ; SVT : 15 → ×2 = 30 ; (44+30)/6 = 12,33
        assertThat(service.averageForStudent(1L, Term.T1, null)).isEqualByComparingTo("12.33");
    }
}
