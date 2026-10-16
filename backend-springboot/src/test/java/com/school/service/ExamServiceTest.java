package com.school.service;

import com.school.dto.request.ExamRequest;
import com.school.dto.response.ExamResponse;
import com.school.dto.response.PageResponse;
import com.school.entity.AcademicYear;
import com.school.entity.Exam;
import com.school.entity.SchoolClass;
import com.school.entity.Subject;
import com.school.enums.ExamStatus;
import com.school.enums.ExamType;
import com.school.enums.Term;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.mapper.ExamMapper;
import com.school.repository.ExamRepository;
import com.school.repository.GradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests du module examens : planification, statuts, délibérations.
 *
 * <p>Le garde le plus important est celui de {@code delete} : les notes sont
 * supprimées <b>en cascade par la base</b> ({@code fk_grades_exam ON DELETE
 * CASCADE}). Sans le comptage préalable, supprimer une évaluation faisait
 * disparaître silencieusement toutes ses notes — exactement la classe de bug
 * qui passe une CI verte.</p>
 *
 * <p>Second invariant : l'année scolaire est <b>résolue par le calendrier</b>,
 * jamais reprise telle quelle du texte de la requête. Une année libre
 * estampillait des notes d'un libellé inexistant et faussait toutes les
 * moyennes.</p>
 */
@ExtendWith(MockitoExtension.class)
class ExamServiceTest {

    private static final LocalDate DATE_EVAL = LocalDate.of(2026, 11, 5);

    @Mock
    private ExamRepository examRepository;
    @Mock
    private ExamMapper examMapper;
    @Mock
    private ClassService classService;
    @Mock
    private SubjectService subjectService;
    @Mock
    private AuditService auditService;
    @Mock
    private GradeRepository gradeRepository;
    @Mock
    private AcademicCalendarService academicCalendarService;

    private ExamService service;

    @BeforeEach
    void setUp() {
        service = new ExamService(examRepository, examMapper, classService, subjectService,
                auditService, gradeRepository, academicCalendarService);
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static SchoolClass schoolClass(Long id, String name) {
        return SchoolClass.builder().id(id).name(name).build();
    }

    private static Subject subject(Long id, String name) {
        return Subject.builder().id(id).name(name).build();
    }

    private static AcademicYear annee(String label) {
        return AcademicYear.builder().label(label).build();
    }

    /** Évaluation complète : {@code ExamResponse.from} déréférence classe et matière. */
    private static Exam exam(Long id, String name) {
        return Exam.builder()
                .id(id).name(name)
                .type(ExamType.DEVOIR).term(Term.T1)
                .academicYear("2026-2027")
                .schoolClass(schoolClass(3L, "6e A"))
                .subject(subject(9L, "Mathématiques"))
                .examDate(DATE_EVAL)
                .coefficient(1)
                .status(ExamStatus.PLANNED)
                .build();
    }

    private static Exam examWithYear(Long id, String year) {
        Exam e = exam(id, "Évaluation " + id);
        e.setAcademicYear(year);
        return e;
    }

    private static ExamRequest requestSansCoefficientNiStatut() {
        return ExamRequest.builder()
                .name("Contrôle 1").type(ExamType.DEVOIR).term(Term.T1)
                .academicYear("2026-2027").classId(3L).subjectId(9L)
                .examDate(DATE_EVAL)
                .coefficient(null).status(null)
                .build();
    }

    // ------------------------------------------------------------------
    // create : valeurs par défaut et année résolue
    // ------------------------------------------------------------------

    @Test
    void create_appliqueLesValeursParDefautQuandLaRequeteEstMuette() {
        ExamRequest request = requestSansCoefficientNiStatut();
        // Le mapper MapStruct ignore explicitement status et coefficient
        // (@Mapping(ignore = true)) : l'entité peut sortir avec ces deux champs
        // à null. Le service doit les renseigner, sinon la base refuse
        // (colonnes NOT NULL).
        Exam mapped = Exam.builder()
                .name(request.getName()).type(request.getType()).term(request.getTerm())
                .academicYear(request.getAcademicYear()).examDate(request.getExamDate())
                .status(null).coefficient(null)
                .build();
        when(examMapper.toEntity(request)).thenReturn(mapped);
        when(classService.findById(3L)).thenReturn(schoolClass(3L, "6e A"));
        when(subjectService.findById(9L)).thenReturn(subject(9L, "Mathématiques"));
        when(academicCalendarService.resolveYear(isNull(), eq("2026-2027"), any()))
                .thenReturn(annee("2026-2027"));
        when(examRepository.save(mapped)).thenReturn(mapped);

        ExamResponse res = service.create(request, null);

        assertThat(res.getStatus()).isEqualTo(ExamStatus.PLANNED);
        assertThat(res.getCoefficient()).isEqualTo(1);
    }

    @Test
    void create_estampilleLAnneeResolueParLeCalendrierEtNonLeTexteDeLaRequete() {
        ExamRequest request = ExamRequest.builder()
                .name("Contrôle 1").type(ExamType.DEVOIR).term(Term.T1)
                .academicYear("2026")           // saisie approximative
                .classId(3L).subjectId(9L)
                .examDate(DATE_EVAL)
                .build();
        Exam mapped = Exam.builder().name(request.getName()).examDate(DATE_EVAL).build();
        when(examMapper.toEntity(request)).thenReturn(mapped);
        when(classService.findById(3L)).thenReturn(schoolClass(3L, "6e A"));
        when(subjectService.findById(9L)).thenReturn(subject(9L, "Mathématiques"));
        // Le calendrier normalise « 2026 » en « 2026-2027 ».
        when(academicCalendarService.resolveYear(isNull(), eq("2026"), any()))
                .thenReturn(annee("2026-2027"));
        when(examRepository.save(mapped)).thenReturn(mapped);

        ExamResponse res = service.create(request, null);

        assertThat(res.getAcademicYear())
                .as("le libellé stocké doit venir du calendrier, pas du texte de la requête")
                .isEqualTo("2026-2027");
        // La date de repli transmise au calendrier est bien celle de l'évaluation.
        verify(academicCalendarService).resolveYear(isNull(), eq("2026"), eq(DATE_EVAL));
    }

    // ------------------------------------------------------------------
    // update : ne pas écraser ce que la requête omet
    // ------------------------------------------------------------------

    @Test
    void update_preserveCoefficientEtStatutQuandLaRequeteLesOmet() {
        Exam existant = exam(11L, "Contrôle 1");
        existant.setCoefficient(3);
        existant.setStatus(ExamStatus.COMPLETED);
        when(examRepository.findById(11L)).thenReturn(Optional.of(existant));
        when(classService.findById(3L)).thenReturn(schoolClass(3L, "6e A"));
        when(subjectService.findById(9L)).thenReturn(subject(9L, "Mathématiques"));
        when(academicCalendarService.resolveYear(isNull(), eq("2026-2027"), any()))
                .thenReturn(annee("2026-2027"));
        when(examRepository.save(existant)).thenReturn(existant);

        ExamRequest request = ExamRequest.builder()
                .name("Contrôle 1").classId(3L).subjectId(9L)
                .coefficient(null).status(null)
                .build();

        ExamResponse res = service.update(11L, request, null);

        assertThat(res.getCoefficient())
                .as("un coefficient omis ne doit pas remettre l'évaluation à 1")
                .isEqualTo(3);
        assertThat(res.getStatus())
                .as("un statut omis ne doit pas faire régresser une évaluation déjà terminée")
                .isEqualTo(ExamStatus.COMPLETED);
    }

    @Test
    void update_appliqueCoefficientEtStatutQuandIlsSontFournis() {
        Exam existant = exam(11L, "Contrôle 1");
        when(examRepository.findById(11L)).thenReturn(Optional.of(existant));
        when(classService.findById(3L)).thenReturn(schoolClass(3L, "6e A"));
        when(subjectService.findById(9L)).thenReturn(subject(9L, "Mathématiques"));
        when(academicCalendarService.resolveYear(isNull(), eq("2026-2027"), any()))
                .thenReturn(annee("2026-2027"));
        when(examRepository.save(existant)).thenReturn(existant);

        ExamRequest request = ExamRequest.builder()
                .name("Contrôle 1").classId(3L).subjectId(9L)
                .coefficient(4).status(ExamStatus.DELIBERATED)
                .build();

        ExamResponse res = service.update(11L, request, null);

        assertThat(res.getCoefficient()).isEqualTo(4);
        assertThat(res.getStatus()).isEqualTo(ExamStatus.DELIBERATED);
    }

    // ------------------------------------------------------------------
    // delete : la garde contre la cascade silencieuse
    // ------------------------------------------------------------------

    @Test
    void delete_refuseQuandDesNotesSontEnregistrees() {
        Exam existant = exam(11L, "Contrôle 1");
        when(examRepository.findById(11L)).thenReturn(Optional.of(existant));
        when(gradeRepository.countByExamId(11L)).thenReturn(7L);

        assertThatThrownBy(() -> service.delete(11L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("7 note(s)");

        // La suppression est en CASCADE côté base : si l'on va jusque-là,
        // les 7 notes disparaissent sans confirmation.
        verify(examRepository, never()).delete(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void delete_supprimeQuandAucuneNoteNEstRattachee() {
        Exam existant = exam(11L, "Contrôle 1");
        when(examRepository.findById(11L)).thenReturn(Optional.of(existant));
        when(gradeRepository.countByExamId(11L)).thenReturn(0L);

        service.delete(11L, null);

        verify(examRepository).delete(existant);
        verify(auditService).log(eq("DELETE"), eq("Exam"), eq(11L),
                contains("Contrôle 1"), isNull());
    }

    // ------------------------------------------------------------------
    // listByClass : filtre par année
    // ------------------------------------------------------------------

    @Test
    void listByClass_sansAnnee_renvoieToutesLesAnneesSansInterrogerLeCalendrier() {
        when(examRepository.findBySchoolClassId(3L)).thenReturn(List.of(
                examWithYear(1L, "2025-2026"), examWithYear(2L, "2026-2027")));

        assertThat(service.listByClass(3L)).hasSize(2);

        // Comportement historique assumé : l'écran d'administration doit
        // pouvoir consulter une année passée.
        verifyNoInteractions(academicCalendarService);
    }

    @Test
    void listByClass_avecAnnee_filtreSurLeLibelleResoluParLeCalendrier() {
        when(examRepository.findBySchoolClassId(3L)).thenReturn(List.of(
                examWithYear(1L, "2025-2026"), examWithYear(2L, "2026-2027")));
        when(academicCalendarService.resolveYear(7L, null, null)).thenReturn(annee("2026-2027"));

        List<ExamResponse> res = service.listByClass(3L, 7L);

        assertThat(res).extracting(ExamResponse::getId).containsExactly(2L);
        verify(academicCalendarService).resolveYear(7L, null, null);
    }

    // ------------------------------------------------------------------
    // Lectures simples
    // ------------------------------------------------------------------

    @Test
    void findById_leveUneResourceNotFoundException() {
        when(examRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void search_transmetLesFiltresEtLeTriParDateDecroissante() {
        Pageable attendu = PageRequest.of(0, 20, Sort.by("examDate").descending());
        when(examRepository.search("controle", 3L, 9L, Term.T1, ExamStatus.PLANNED, attendu))
                .thenReturn(new PageImpl<>(List.of(exam(1L, "Contrôle 1")), attendu, 1));

        PageResponse<ExamResponse> res =
                service.search("controle", 3L, 9L, Term.T1, ExamStatus.PLANNED, 0, 20);

        assertThat(res.getTotalElements()).isEqualTo(1);
        verify(examRepository).search("controle", 3L, 9L, Term.T1, ExamStatus.PLANNED, attendu);
    }

    @Test
    void changeStatus_appliqueLeNouveauStatutEtJournalise() {
        Exam existant = exam(11L, "Contrôle 1");
        when(examRepository.findById(11L)).thenReturn(Optional.of(existant));
        when(examRepository.save(existant)).thenReturn(existant);

        ExamResponse res = service.changeStatus(11L, ExamStatus.DELIBERATED, null);

        assertThat(res.getStatus()).isEqualTo(ExamStatus.DELIBERATED);
        verify(auditService).log(eq("DELIBERATE"), eq("Exam"), eq(11L),
                contains("DELIBERATED"), isNull());
    }

    @Test
    void upcoming_delegueAuDepotSurLaPlageDeDates() {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 30);
        when(examRepository.findByExamDateBetween(from, to)).thenReturn(List.of(exam(1L, "Contrôle 1")));

        assertThat(service.upcoming(from, to)).hasSize(1);
    }
}
