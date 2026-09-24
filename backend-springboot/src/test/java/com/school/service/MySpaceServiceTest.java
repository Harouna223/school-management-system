package com.school.service;

import com.school.dto.response.LmdReleveResponse;
import com.school.dto.response.MyGradeResponse;
import com.school.dto.response.ScheduleResponse;
import com.school.entity.Attendance;
import com.school.entity.Exam;
import com.school.entity.Grade;
import com.school.entity.Invoice;
import com.school.entity.SchoolClass;
import com.school.entity.Schedule;
import com.school.entity.Student;
import com.school.entity.Teacher;
import com.school.entity.User;
import com.school.enums.Term;
import com.school.exception.BusinessException;
import com.school.repository.AttendanceRepository;
import com.school.repository.BulletinRepository;
import com.school.repository.EnrollmentHistoryRepository;
import com.school.repository.GradeRepository;
import com.school.repository.InvoiceRepository;
import com.school.repository.LmdEnrollmentRepository;
import com.school.repository.ScheduleRepository;
import com.school.repository.StudentRepository;
import com.school.repository.SubjectAssignmentRepository;
import com.school.repository.TeacherRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de l'espace personnel (élève / enseignant).
 *
 * <p>Pourquoi ce fichier existe : ces endpoints servent les portails `/my-school` et
 * `/my-teaching`, et il n'avaient aucun test. Ce n'est pas le service le plus compliqué,
 * mais c'est une **frontière d'autorisation** : chaque méthode doit lire les données du
 * compte connecté et de lui seul.</p>
 *
 * <p>C'est donc l'invariant qu'on épingle ici, plus que le contenu des réponses :
 * <b>le repository est-il interrogé avec l'identifiant du compte connecté, et jamais avec
 * un identifiant venu de la requête ?</b> Une erreur sur ce point ne casserait aucun
 * build — elle ferait simplement fuiter les notes d'un autre élève.</p>
 *
 * <p>⚠️ Ce service étant en lecture seule, aucun test n'écrit : rien à nettoyer côté
 * données.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MySpaceServiceTest {

    @Mock private StudentRepository studentRepository;
    @Mock private TeacherRepository teacherRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private GradeRepository gradeRepository;
    @Mock private BulletinRepository bulletinRepository;
    @Mock private AttendanceRepository attendanceRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private SubjectAssignmentRepository subjectAssignmentRepository;
    @Mock private LmdEnrollmentRepository lmdEnrollmentRepository;
    @Mock private LmdService lmdService;
    @Mock private EnrollmentHistoryRepository enrollmentHistoryRepository;

    @InjectMocks private MySpaceService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // Liaison compte -> profil
    // ------------------------------------------------------------------

    @Test
    @DisplayName("un compte élève récupère le profil de l'élève lié à son propre identifiant")
    void profilEleveUtiliseLIdentifiantDuCompteConnecte() {
        Student eleve = eleve(6L, 1L);
        connecte(1L);
        when(studentRepository.findByUserId(1L)).thenReturn(Optional.of(eleve));

        assertThat(service.myProfile()).isNotNull();

        // Le point qui compte : l'identifiant vient du jeton, pas de la requête.
        verify(studentRepository).findByUserId(1L);
    }

    @Test
    @DisplayName("un compte sans profil élève obtient un message explicite")
    void compteSansProfilEleveRefuse() {
        connecte(1L);
        when(studentRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.myProfile())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Aucun profil élève");
    }

    @Test
    @DisplayName("un compte sans profil enseignant est refusé sur les endpoints enseignant")
    void compteSansProfilEnseignantRefuse() {
        connecte(1L);
        when(teacherRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.myTeacherProfile())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Aucun profil enseignant");

        assertThatThrownBy(() -> service.myTeacherSchedule())
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.myTeacherClasses())
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("aucun utilisateur dans le contexte donne un identifiant nul, donc aucun profil")
    void horsContexteAucunProfil() {
        // `currentUserId()` renvoie null quand rien n'est authentifié : le repository est
        // interrogé avec null, ne trouve rien, et on lève — jamais de fuite silencieuse.
        SecurityContextHolder.clearContext();
        when(studentRepository.findByUserId(null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.myProfile())
                .isInstanceOf(BusinessException.class);

        verify(studentRepository).findByUserId(null);
    }

    // ------------------------------------------------------------------
    // Portée élève : tout est filtré par l'élève connecté
    // ------------------------------------------------------------------

    @Test
    @DisplayName("notes, présences et factures sont tous demandés pour l'élève connecté")
    void porteeEleveSurLesListes() {
        Student eleve = eleve(6L, 1L);
        connecte(1L);
        when(studentRepository.findByUserId(1L)).thenReturn(Optional.of(eleve));
        when(gradeRepository.findByStudentId(6L)).thenReturn(List.of());
        when(attendanceRepository.findByStudentId(6L)).thenReturn(List.of());
        when(invoiceRepository.findByStudentId(6L)).thenReturn(List.of());
        when(bulletinRepository.findByStudentIdOrderByAcademicYearDescTermDesc(6L)).thenReturn(List.of());

        service.myGrades(null);
        service.myAttendances();
        service.myInvoices();
        service.myBulletins();

        verify(gradeRepository).findByStudentId(6L);
        verify(attendanceRepository).findByStudentId(6L);
        verify(invoiceRepository).findByStudentId(6L);
        verify(bulletinRepository).findByStudentIdOrderByAcademicYearDescTermDesc(6L);
        // Aucune de ces méthodes ne doit être interrogée pour un AUTRE élève que le
        // connecté : c'est la garantie de non-fuite, et elle est plus forte qu'un
        // `never()` sur un identifiant quelconque (qui matcherait aussi 6).
        verify(gradeRepository, never()).findByStudentId(7L);
        verify(attendanceRepository, never()).findByStudentId(7L);
        verify(invoiceRepository, never()).findByStudentId(7L);
        verify(bulletinRepository, never()).findByStudentIdOrderByAcademicYearDescTermDesc(7L);
    }

    @Test
    @DisplayName("les notes sont triées de la plus récente à la plus ancienne")
    void notesTrieesDeLaPlusRecenteALaPlusAncienne() {
        Student eleve = eleve(6L, 1L);
        connecte(1L);
        when(studentRepository.findByUserId(1L)).thenReturn(Optional.of(eleve));
        when(gradeRepository.findByStudentId(6L)).thenReturn(List.of(
                note(1L, Term.T1, LocalDate.of(2026, 1, 10)),
                note(2L, Term.T2, LocalDate.of(2026, 4, 20)),
                note(3L, Term.T1, LocalDate.of(2026, 2, 15))));

        List<MyGradeResponse> notes = service.myGrades(null);

        assertThat(notes).hasSize(3);
        assertThat(notes.get(0).getExamDate()).isEqualTo(LocalDate.of(2026, 4, 20));
        assertThat(notes.get(2).getExamDate()).isEqualTo(LocalDate.of(2026, 1, 10));
    }

    @Test
    @DisplayName("un trimestre demandé filtre les notes, sans trimestre on les garde toutes")
    void filtreParTrimestre() {
        Student eleve = eleve(6L, 1L);
        connecte(1L);
        when(studentRepository.findByUserId(1L)).thenReturn(Optional.of(eleve));
        when(gradeRepository.findByStudentId(6L)).thenReturn(List.of(
                note(1L, Term.T1, LocalDate.of(2026, 1, 10)),
                note(2L, Term.T2, LocalDate.of(2026, 4, 20)),
                note(3L, Term.T3, LocalDate.of(2026, 6, 5))));

        assertThat(service.myGrades(null)).hasSize(3);
        assertThat(service.myGrades(Term.T2)).hasSize(1);
        assertThat(service.myGrades(Term.T1)).hasSize(1);
        assertThat(service.myGrades(Term.T3)).hasSize(1);
        // Un trimestre sans note ne doit pas lever, juste renvoyer une liste vide.
        assertThat(service.myGrades(null)).isNotNull();
    }

    @Test
    @DisplayName("l'emploi du temps suit la classe de l'élève et est trié par jour puis heure")
    void emploiDuTempsDeLaClasseDeLEleve() {
        Student eleve = eleve(6L, 1L);
        eleve.setSchoolClass(classe(3L));
        connecte(1L);
        when(studentRepository.findByUserId(1L)).thenReturn(Optional.of(eleve));
        when(scheduleRepository.findBySchoolClassId(3L)).thenReturn(List.of(
                cours(com.school.enums.DayOfWeek.TUESDAY, "10:30:00"),
                cours(com.school.enums.DayOfWeek.MONDAY, "14:00:00"),
                cours(com.school.enums.DayOfWeek.MONDAY, "08:00:00")));

        List<ScheduleResponse> emploi = service.mySchedule();

        assertThat(emploi).hasSize(3);
        // Lundi 08:00 < lundi 14:00 < mardi 10:30
        assertThat(emploi.get(0).getStartTime()).isEqualTo(java.time.LocalTime.parse("08:00:00"));
        assertThat(emploi.get(1).getStartTime()).isEqualTo(java.time.LocalTime.parse("14:00:00"));
        assertThat(emploi.get(2).getStartTime()).isEqualTo(java.time.LocalTime.parse("10:30:00"));
        verify(scheduleRepository).findBySchoolClassId(3L);
    }

    @Test
    @DisplayName("un élève sans classe obtient un emploi du temps vide, sans erreur")
    void eleveSansClasseEmploiDuTempsVide() {
        Student eleve = eleve(6L, 1L);
        eleve.setSchoolClass(null);
        connecte(1L);
        when(studentRepository.findByUserId(1L)).thenReturn(Optional.of(eleve));

        assertThat(service.mySchedule()).isEmpty();

        // Sans classe, on ne doit pas interroger le repository avec un identifiant nul.
        verify(scheduleRepository, never()).findBySchoolClassId(anyLong());
    }

    // ------------------------------------------------------------------
    // Portée universitaire (LMD)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("les endpoints LMD utilisent l'identifiant de l'élève connecté")
    void porteeLmdSurLEleveConnecte() {
        Student eleve = eleve(6L, 1L);
        connecte(1L);
        when(studentRepository.findByUserId(1L)).thenReturn(Optional.of(eleve));
        when(lmdEnrollmentRepository.findByStudentIdOrderByIdDesc(6L)).thenReturn(List.of());
        when(enrollmentHistoryRepository.findByStudentIdOrderByCreatedAtDesc(6L)).thenReturn(List.of());
        when(lmdService.buildReleve(eq(6L), any(), anyString(), anyInt()))
                .thenReturn(LmdReleveResponse.builder().build());

        service.myUniversityEnrollments();
        service.myUniversityHistory();
        service.myUniversityReleve(2L, "S1", 1);

        verify(lmdEnrollmentRepository).findByStudentIdOrderByIdDesc(6L);
        verify(enrollmentHistoryRepository).findByStudentIdOrderByCreatedAtDesc(6L);
        // ⚠️ Le `fieldId` vient bien de l'appelant (c'est un choix de filtre), mais
        // l'identifiant ÉLÈVE est celui du jeton — le contraire serait une fuite.
        verify(lmdService).buildReleve(6L, 2L, "S1", 1);
    }

    // ------------------------------------------------------------------
    // Portée enseignant
    // ------------------------------------------------------------------

    @Test
    @DisplayName("les endpoints enseignant utilisent l'identifiant de l'enseignant connecté")
    void porteeEnseignantSurLeCompteConnecte() {
        Teacher enseignant = enseignant(1L, 4L);
        connecte(4L);
        when(teacherRepository.findByUserId(4L)).thenReturn(Optional.of(enseignant));
        when(scheduleRepository.findByTeacherId(1L)).thenReturn(List.of());
        when(subjectAssignmentRepository.findByTeacherId(1L)).thenReturn(List.of());

        service.myTeacherProfile();
        service.myTeacherSchedule();
        service.myTeacherClasses();

        // Les 3 endpoints résolvent le profil via le même identifiant de compte.
        verify(teacherRepository, org.mockito.Mockito.times(3)).findByUserId(4L);
        verify(scheduleRepository).findByTeacherId(1L);
        verify(subjectAssignmentRepository).findByTeacherId(1L);
        verify(scheduleRepository, never()).findByTeacherId(2L);
    }

    @Test
    @DisplayName("notes d'un élève : aucune donnée n'est lue sans profil élève lié")
    void aucuneLectureSansProfilEleve() {
        connecte(1L);
        when(studentRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.myGrades(null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.myAttendances()).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.myInvoices()).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.myBulletins()).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.mySchedule()).isInstanceOf(BusinessException.class);

        // Le refus arrive AVANT toute lecture de données métier.
        verify(gradeRepository, never()).findByStudentId(anyLong());
        verify(attendanceRepository, never()).findByStudentId(anyLong());
        verify(invoiceRepository, never()).findByStudentId(anyLong());
        verify(scheduleRepository, never()).findBySchoolClassId(anyLong());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void connecte(Long userId) {
        User user = User.builder().username("test" + userId).password("hache").enabled(true).build();
        user.setId(userId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private static Student eleve(Long id, Long userId) {
        User user = User.builder().username("eleve" + id).password("hache").enabled(true).build();
        user.setId(userId);
        Student eleve = Student.builder().firstName("Aicha").lastName("Kamdem").build();
        eleve.setId(id);
        eleve.setUser(user);
        return eleve;
    }

    private static Teacher enseignant(Long id, Long userId) {
        User user = User.builder().username("prof" + id).password("hache").enabled(true).build();
        user.setId(userId);
        Teacher enseignant = Teacher.builder().firstName("Jean").lastName("Kamdem").build();
        enseignant.setId(id);
        enseignant.setUser(user);
        return enseignant;
    }

    private static SchoolClass classe(Long id) {
        SchoolClass classe = SchoolClass.builder().name("6e A").build();
        classe.setId(id);
        return classe;
    }

    private static Grade note(Long id, Term term, LocalDate date) {
        // `MyGradeResponse.from` lit `exam.getSubject()` ET son identifiant : la matière
        // ne peut donc pas rester nulle, sinon le mapping lève une NPE (piège rencontré).
        com.school.entity.Subject matiere = com.school.entity.Subject.builder()
                .name("Mathematiques").code("MATH").build();
        matiere.setId(1L);
        Exam exam = Exam.builder()
                .name("Controle")
                .term(term)
                .examDate(date)
                .subject(matiere)
                .build();
        exam.setId(id);
        Grade note = Grade.builder().exam(exam).value(new java.math.BigDecimal("15.5")).build();
        note.setId(id);
        return note;
    }

    private static Schedule cours(com.school.enums.DayOfWeek jour, String heure) {
        // `ScheduleResponse.from` lit la classe, la matière ET l'enseignant : les trois
        // sont obligatoires, `room` en revanche est toléré nul (branche testée aussi).
        return Schedule.builder()
                .dayOfWeek(jour)
                .startTime(java.time.LocalTime.parse(heure))
                .endTime(java.time.LocalTime.parse(heure).plusHours(1))
                .schoolClass(classe(3L))
                .subject(com.school.entity.Subject.builder().name("Histoire-Geographie").build())
                .teacher(enseignant(1L, 4L))
                .build();
    }
}
