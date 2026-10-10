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

    private GradeService service;

    @BeforeEach
    void setUp() {
        service = new GradeService(gradeRepository, studentRepository, bulletinRepository,
                notificationRepository, examService, auditService, reportService,
                whatsappService, emailService, smsService);
    }

    private GradeRequest request(String value, String maxValue) {
        return GradeRequest.builder()
                .studentId(10L)
                .examId(1L)
                .value(value != null ? new BigDecimal(value) : null)
                .maxValue(maxValue != null ? new BigDecimal(maxValue) : null)
                .build();
    }

    @Test
    void negativeGradeIsRejected() {
        when(examService.findById(1L)).thenReturn(Exam.builder().id(1L).build());
        when(studentRepository.findById(10L))
                .thenReturn(java.util.Optional.of(Student.builder().id(10L).build()));

        assertThatThrownBy(() -> service.save(request("-1", null), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("négative");
    }

    @Test
    void gradeAboveTwentyIsRejected() {
        when(examService.findById(1L)).thenReturn(Exam.builder().id(1L).build());
        when(studentRepository.findById(10L))
                .thenReturn(java.util.Optional.of(Student.builder().id(10L).build()));

        assertThatThrownBy(() -> service.save(request("21", null), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ne peut pas dépasser");
    }

    @Test
    void gradeAboveCustomMaxIsRejected() {
        when(examService.findById(1L)).thenReturn(Exam.builder().id(1L).build());
        when(studentRepository.findById(10L))
                .thenReturn(java.util.Optional.of(Student.builder().id(10L).build()));

        assertThatThrownBy(() -> service.save(request("15", "10"), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ne peut pas dépasser");
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
        when(gradeRepository.findByStudentIdAndTerm(10L, Term.T1)).thenReturn(List.of(
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
        when(gradeRepository.findByStudentIdAndTerm(10L, Term.T1)).thenReturn(List.of(
                grade(new BigDecimal("10"), "20", 2, 1),
                grade(new BigDecimal("18"), "20", 1, 1)
        ));

        BigDecimal moyenne = service.averageForStudent(10L, Term.T1);

        assertThat(moyenne).isEqualByComparingTo("12.67");
    }

    @Test
    @DisplayName("aucune note => moyenne null (jamais 0)")
    void moyenneNulleSansNotes() {
        when(gradeRepository.findByStudentIdAndTerm(10L, Term.T1)).thenReturn(List.of());

        assertThat(service.averageForStudent(10L, Term.T1)).isNull();
    }

    @Test
    @DisplayName("une maxValue maximale de l'échelle est conservée à la norme 20 par défaut")
    void maxValueDefautA20QuandAbsent() {
        when(gradeRepository.findByStudentIdAndTerm(10L, Term.T1)).thenReturn(List.of(
                grade(new BigDecimal("12"), "20", 1, 1)
        ));

        assertThat(service.averageForStudent(10L, Term.T1)).isEqualByComparingTo("12.00");
    }

    // ============================================================ resauvegarde

    @Test
    @DisplayName("ré-enregistrer une note met à jour son échelle (maxValue)")
    void resauvegardeMetAJourMaxValue() {
        when(examService.findById(1L)).thenReturn(Exam.builder().id(1L).build());
        when(studentRepository.findById(10L))
                .thenReturn(Optional.of(Student.builder().id(10L).build()));
        Grade existante = Grade.builder()
                .id(100L)
                .student(Student.builder().id(10L).firstName("Test").lastName("Student").matricule("ST-10").build())
                .exam(Exam.builder().id(1L).name("Évaluation")
                        .subject(Subject.builder().id(5L).name("Matière").build()).build())
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
}
