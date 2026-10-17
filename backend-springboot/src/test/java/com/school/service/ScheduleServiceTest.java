package com.school.service;

import com.school.dto.request.ScheduleRequest;
import com.school.dto.response.ScheduleResponse;
import com.school.entity.Room;
import com.school.entity.Schedule;
import com.school.entity.SchoolClass;
import com.school.entity.Subject;
import com.school.entity.Teacher;
import com.school.enums.DayOfWeek;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.ScheduleRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

/**
 * Emploi du temps — validation des horaires, detection de conflits, et journalisation d'audit.
 *
 * <p><b>Le piege central de ce service : {@code ScheduleRepository.isConflict} est une methode
 * {@code default} qui contient la vraie logique de chevauchement.</b> Un {@code @Mock} ordinaire
 * n'execute pas les methodes {@code default} : il renvoie {@code false}. Un test qui mocke le
 * repository prouverait donc que le service appelle une methode… qui ne fait rien, et passerait au
 * vert meme si la detection de conflits etait entierement cassee. C'est exactement le piege du
 * mapper mocke ({@code updateEntity()} inerte).</p>
 *
 * <p>D'ou le montage : le repository est mocke avec {@link org.mockito.Mockito#CALLS_REAL_METHODS}
 * et seul {@code findByDayOfWeek} est stubbe. La logique de chevauchement s'execute alors pour de
 * vrai, et les tests de conflit valident le comportement complet plutot qu'un stub.</p>
 *
 * <p>Deux invariants valent d'etre isoles :</p>
 * <ol>
 *   <li><b>Le chevauchement est strict.</b> {@code start.isBefore(existing.end) &&
 *       end.isAfter(existing.start)} — deux creneaux adjacents (10h-11h puis 11h-12h) ne sont
 *       <i>pas</i> en conflit. Passer a {@code !isAfter}/{@code !isBefore} interdirait
 *       l'enchainement normal des cours.</li>
 *   <li><b>{@code excludeId} vaut l'identifiant en modification.</b> Avec {@code null},
 *       un creneau entre en conflit avec lui-meme et <i>toute</i> modification echoue.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    private static final LocalTime DIX_HEURES = LocalTime.of(10, 0);
    private static final LocalTime ONZE_HEURES = LocalTime.of(11, 0);

    @Mock
    private ClassService classService;
    @Mock
    private SubjectService subjectService;
    @Mock
    private TeacherService teacherService;
    @Mock
    private AuditService auditService;
    @Mock
    private HttpServletRequest httpRequest;

    /** Mocke mais avec les methodes {@code default} reellement executees. */
    private ScheduleRepository scheduleRepository;
    private ScheduleService service;

    private SchoolClass classe;
    private Subject matiere;
    private Teacher enseignant;

    @BeforeEach
    void setUp() {
        scheduleRepository = mock(ScheduleRepository.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        service = new ScheduleService(scheduleRepository, classService, subjectService,
                teacherService, auditService);

        classe = schoolClass(1L, "6e A");
        matiere = subject(2L, "Mathematiques");
        enseignant = teacher(3L, "Jean", "Kamdem");
    }

    // ==================================================================
    // Fixtures
    // ==================================================================

    private static SchoolClass schoolClass(Long id, String name) {
        SchoolClass c = new SchoolClass();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private static Subject subject(Long id, String name) {
        Subject s = new Subject();
        s.setId(id);
        s.setName(name);
        return s;
    }

    private static Teacher teacher(Long id, String firstName, String lastName) {
        Teacher t = new Teacher();
        t.setId(id);
        t.setFirstName(firstName);
        t.setLastName(lastName);
        return t;
    }

    private static Room room(Long id, String name) {
        Room r = new Room();
        r.setId(id);
        r.setName(name);
        return r;
    }

    private static Schedule creneau(Long id, DayOfWeek day, LocalTime start, LocalTime end,
                                    SchoolClass classe, Teacher enseignant, Room salle) {
        return Schedule.builder()
                .id(id)
                .dayOfWeek(day)
                .startTime(start)
                .endTime(end)
                .schoolClass(classe)
                .teacher(enseignant)
                .room(salle)
                .build();
    }

    private static ScheduleRequest requete(LocalTime start, LocalTime end, Long roomId) {
        return ScheduleRequest.builder()
                .dayOfWeek(DayOfWeek.MONDAY)
                .startTime(start)
                .endTime(end)
                .classId(1L)
                .subjectId(2L)
                .teacherId(3L)
                .roomId(roomId)
                .build();
    }

    /** Stubs communs a tout {@code create} qui franchit la validation. */
    private void resolveLesRelations() {
        when(classService.findById(1L)).thenReturn(classe);
        when(subjectService.findById(2L)).thenReturn(matiere);
        when(teacherService.findById(3L)).thenReturn(enseignant);
    }

    // ==================================================================
    // 1. La logique de conflit, executee pour de vrai (methode default)
    // ==================================================================

    @Nested
    @DisplayName("ScheduleRepository.isConflict — logique reelle")
    class DetectionDeConflits {

        @Test
        void memeClasseSurUnCreneauChevauchant_estUnConflit() {
            when(scheduleRepository.findByDayOfWeek(DayOfWeek.MONDAY)).thenReturn(List.of(
                    creneau(99L, DayOfWeek.MONDAY, DIX_HEURES, ONZE_HEURES, classe, enseignant, null)));

            boolean conflit = service.hasConflict(DayOfWeek.MONDAY, LocalTime.of(10, 30),
                    LocalTime.of(11, 30), 1L, 77L, null, null);

            assertThat(conflit).isTrue();
        }

        @Test
        void memeEnseignantSurUnCreneauChevauchant_estUnConflit() {
            when(scheduleRepository.findByDayOfWeek(DayOfWeek.MONDAY)).thenReturn(List.of(
                    creneau(99L, DayOfWeek.MONDAY, DIX_HEURES, ONZE_HEURES, classe, enseignant, null)));

            boolean conflit = service.hasConflict(DayOfWeek.MONDAY, LocalTime.of(9, 0),
                    LocalTime.of(10, 30), 42L, 3L, null, null);

            assertThat(conflit).isTrue();
        }

        @Test
        void memeSalleSurUnCreneauChevauchant_estUnConflit() {
            when(scheduleRepository.findByDayOfWeek(DayOfWeek.MONDAY)).thenReturn(List.of(
                    creneau(99L, DayOfWeek.MONDAY, DIX_HEURES, ONZE_HEURES, classe, enseignant,
                            room(9L, "Salle 9"))));

            boolean conflit = service.hasConflict(DayOfWeek.MONDAY, LocalTime.of(10, 30),
                    LocalTime.of(11, 30), 42L, 77L, 9L, null);

            assertThat(conflit).isTrue();
        }

        @Test
        void classeEnseignantEtSalleDifferents_neCreeAucunConflit() {
            when(scheduleRepository.findByDayOfWeek(DayOfWeek.MONDAY)).thenReturn(List.of(
                    creneau(99L, DayOfWeek.MONDAY, DIX_HEURES, ONZE_HEURES, classe, enseignant,
                            room(9L, "Salle 9"))));

            boolean conflit = service.hasConflict(DayOfWeek.MONDAY, LocalTime.of(10, 30),
                    LocalTime.of(11, 30), 42L, 77L, 12L, null);

            assertThat(conflit).isFalse();
        }

        @Test
        void creneauxAdjacents_neSontPasEnConflit() {
            // 10h-11h puis 11h-12h : la fin de l'un est le debut de l'autre.
            // Le chevauchement doit rester STRICT, sinon aucun enchainement de cours n'est possible.
            when(scheduleRepository.findByDayOfWeek(DayOfWeek.MONDAY)).thenReturn(List.of(
                    creneau(99L, DayOfWeek.MONDAY, DIX_HEURES, ONZE_HEURES, classe, enseignant, null)));

            boolean conflit = service.hasConflict(DayOfWeek.MONDAY, ONZE_HEURES,
                    LocalTime.of(12, 0), 1L, 3L, null, null);

            assertThat(conflit)
                    .as("10h-11h et 11h-12h se touchent sans se chevaucher : "
                            + "isBefore/isAfter stricts, pas !isAfter/!isBefore")
                    .isFalse();
        }

        @Test
        void unSeulMinuteDeChevauchementSuffit() {
            when(scheduleRepository.findByDayOfWeek(DayOfWeek.MONDAY)).thenReturn(List.of(
                    creneau(99L, DayOfWeek.MONDAY, DIX_HEURES, ONZE_HEURES, classe, enseignant, null)));

            boolean conflit = service.hasConflict(DayOfWeek.MONDAY, LocalTime.of(10, 59),
                    LocalTime.of(12, 0), 1L, 3L, null, null);

            assertThat(conflit).isTrue();
        }

        @Test
        void excludeId_ignoreLeCreneauQuOnEstEnTrainDeModifier() {
            Schedule existant = creneau(5L, DayOfWeek.MONDAY, DIX_HEURES, ONZE_HEURES,
                    classe, enseignant, null);
            when(scheduleRepository.findByDayOfWeek(DayOfWeek.MONDAY)).thenReturn(List.of(existant));

            boolean conflit = service.hasConflict(DayOfWeek.MONDAY, DIX_HEURES, ONZE_HEURES,
                    1L, 3L, null, 5L);

            assertThat(conflit)
                    .as("sans excludeId, un creneau entre en conflit avec lui-meme")
                    .isFalse();
        }

        @Test
        void sansExcludeId_leCreneauSeDetecteLuiMeme() {
            Schedule existant = creneau(5L, DayOfWeek.MONDAY, DIX_HEURES, ONZE_HEURES,
                    classe, enseignant, null);
            when(scheduleRepository.findByDayOfWeek(DayOfWeek.MONDAY)).thenReturn(List.of(existant));

            boolean conflit = service.hasConflict(DayOfWeek.MONDAY, DIX_HEURES, ONZE_HEURES,
                    1L, 3L, null, null);

            assertThat(conflit).isTrue();
        }

        @Test
        void salleNonDemandee_neCreePasDeConflitDeSalle() {
            when(scheduleRepository.findByDayOfWeek(DayOfWeek.MONDAY)).thenReturn(List.of(
                    creneau(99L, DayOfWeek.MONDAY, DIX_HEURES, ONZE_HEURES, classe, enseignant,
                            room(9L, "Salle 9"))));

            boolean conflit = service.hasConflict(DayOfWeek.MONDAY, DIX_HEURES, ONZE_HEURES,
                    42L, 77L, null, null);

            assertThat(conflit).isFalse();
        }

        @Test
        void candidatSansSalle_neLevePasDException() {
            when(scheduleRepository.findByDayOfWeek(DayOfWeek.MONDAY)).thenReturn(List.of(
                    creneau(99L, DayOfWeek.MONDAY, DIX_HEURES, ONZE_HEURES, classe, enseignant, null)));

            boolean conflit = service.hasConflict(DayOfWeek.MONDAY, DIX_HEURES, ONZE_HEURES,
                    42L, 77L, 9L, null);

            assertThat(conflit).isFalse();
        }
    }

    // ==================================================================
    // 2. Validation des horaires — deux copies distinctes du garde
    // ==================================================================

    @Nested
    @DisplayName("Validation start/end")
    class ValidationDesHoraires {

        @Test
        void create_refuseUneFinAvantLeDebut_sansToucherLaBase() {
            ScheduleRequest requete = requete(LocalTime.of(11, 0), LocalTime.of(10, 0), null);

            assertThatThrownBy(() -> service.create(requete, httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("L'heure de fin doit");

            verifyNoInteractions(scheduleRepository);
        }

        @Test
        void create_refuseUneFinEgaleAuDebut() {
            ScheduleRequest requete = requete(DIX_HEURES, DIX_HEURES, null);

            assertThatThrownBy(() -> service.create(requete, httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("L'heure de fin doit");

            verifyNoInteractions(scheduleRepository);
        }

        @Test
        void update_refuseUneFinAvantLeDebut() {
            // Deuxieme copie du meme garde : la neutraliser dans create ne suffit pas.
            when(scheduleRepository.findById(5L)).thenReturn(Optional.of(
                    creneau(5L, DayOfWeek.MONDAY, DIX_HEURES, ONZE_HEURES, classe, enseignant, null)));

            ScheduleRequest requete = requete(LocalTime.of(11, 0), LocalTime.of(10, 0), null);

            assertThatThrownBy(() -> service.update(5L, requete, httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("L'heure de fin doit");

            verify(scheduleRepository, never()).findByDayOfWeek(any());
            verify(scheduleRepository, never()).save(any());
        }
    }

    // ==================================================================
    // 3. Conflit vu depuis le service
    // ==================================================================

    @Nested
    @DisplayName("Conflit via create/update")
    class ConflitDuService {

        @Test
        void create_refuseUnCreneauEnConflit() {
            // Aucune relation n'est resolue : le conflit est detecte AVANT de charger
            // classe/matiere/enseignant, donc avant toute ecriture.
            when(scheduleRepository.findByDayOfWeek(DayOfWeek.MONDAY)).thenReturn(List.of(
                    creneau(99L, DayOfWeek.MONDAY, DIX_HEURES, ONZE_HEURES, classe, enseignant, null)));

            ScheduleRequest requete = requete(LocalTime.of(10, 30), LocalTime.of(11, 30), null);

            assertThatThrownBy(() -> service.create(requete, httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Conflit");

            verifyNoInteractions(classService);
            verify(scheduleRepository, never()).save(any());
        }

        @Test
        void update_reussiQuandSeulLeCreneauLuiMemeCorrespond() {
            // Le test decisif : l'unique creneau qui chevauche est celui qu'on modifie.
            // Si le service passait excludeId=null, il entrerait en conflit avec lui-meme.
            Schedule existant = creneau(5L, DayOfWeek.MONDAY, DIX_HEURES, ONZE_HEURES,
                    classe, enseignant, null);
            when(scheduleRepository.findById(5L)).thenReturn(Optional.of(existant));
            when(scheduleRepository.findByDayOfWeek(DayOfWeek.MONDAY)).thenReturn(List.of(existant));
            when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));
            resolveLesRelations();

            ScheduleResponse response = service.update(5L, requete(DIX_HEURES, ONZE_HEURES, null),
                    httpRequest);

            assertThat(response.getStartTime()).isEqualTo(DIX_HEURES);
            assertThat(response.getEndTime()).isEqualTo(ONZE_HEURES);
            verify(scheduleRepository).findByDayOfWeek(DayOfWeek.MONDAY);
        }
    }

    // ==================================================================
    // 4. Salle optionnelle
    // ==================================================================

    @Nested
    @DisplayName("Salle optionnelle")
    class SalleOptionnelle {

        @Test
        void create_sansSalle_neDemandePasLaSalle() {
            resolveLesRelations();
            when(scheduleRepository.findByDayOfWeek(DayOfWeek.MONDAY)).thenReturn(List.of());
            when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> {
                Schedule s = inv.getArgument(0);
                s.setId(42L);
                return s;
            });

            ScheduleResponse response = service.create(requete(DIX_HEURES, ONZE_HEURES, null),
                    httpRequest);

            // anyLong() est un matcher PRIMITIF : il ne matche pas null. Avec anyLong(),
            // getRoom(null) passerait inapercu et le garde ne serait pas reellement teste.
            verify(classService, never()).getRoom(any());
            assertThat(response.getRoomId()).isNull();
            assertThat(response.getRoomName()).isNull();
        }

        @Test
        void create_avecSalle_resoutLaSalle() {
            resolveLesRelations();
            when(classService.getRoom(9L)).thenReturn(room(9L, "Salle 9"));
            when(scheduleRepository.findByDayOfWeek(DayOfWeek.MONDAY)).thenReturn(List.of());
            when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> {
                Schedule s = inv.getArgument(0);
                s.setId(42L);
                return s;
            });

            ScheduleResponse response = service.create(requete(DIX_HEURES, ONZE_HEURES, 9L),
                    httpRequest);

            assertThat(response.getRoomId()).isEqualTo(9L);
            assertThat(response.getRoomName()).isEqualTo("Salle 9");
        }
    }

    // ==================================================================
    // 5. Audit, suppression, lecture
    // ==================================================================

    @Nested
    @DisplayName("Audit et persistance")
    class AuditEtPersistance {

        @Test
        void create_journaliseLAuditApresLaSauvegarde() {
            resolveLesRelations();
            when(scheduleRepository.findByDayOfWeek(DayOfWeek.MONDAY)).thenReturn(List.of());
            when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> {
                Schedule s = inv.getArgument(0);
                s.setId(42L);
                return s;
            });

            service.create(requete(DIX_HEURES, ONZE_HEURES, null), httpRequest);

            InOrder ordre = inOrder(scheduleRepository, auditService);
            ordre.verify(scheduleRepository).save(any(Schedule.class));
            ordre.verify(auditService).log("CREATE", "Schedule", 42L,
                    "Création créneau emploi du temps", httpRequest);
        }

        @Test
        void delete_journaliseLAuditAvantLaSuppression() {
            Schedule existant = creneau(5L, DayOfWeek.MONDAY, DIX_HEURES, ONZE_HEURES,
                    classe, enseignant, null);
            when(scheduleRepository.findById(5L)).thenReturn(Optional.of(existant));

            service.delete(5L, httpRequest);

            InOrder ordre = inOrder(auditService, scheduleRepository);
            ordre.verify(auditService).log("DELETE", "Schedule", 5L,
                    "Suppression créneau emploi du temps", httpRequest);
            ordre.verify(scheduleRepository).delete(existant);
        }

        @Test
        void findById_leveUneExceptionSiLeCreneauExistePas() {
            when(scheduleRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(404L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("404");
        }

        @Test
        void listByClass_mappeLesCreneauxEtLaisseLaSalleNulle() {
            Schedule avecSalle = creneau(1L, DayOfWeek.MONDAY, DIX_HEURES, ONZE_HEURES,
                    classe, enseignant, room(9L, "Salle 9"));
            Schedule sansSalle = creneau(2L, DayOfWeek.TUESDAY, ONZE_HEURES, LocalTime.of(12, 0),
                    classe, enseignant, null);
            avecSalle.setSubject(matiere);
            sansSalle.setSubject(matiere);
            when(scheduleRepository.findBySchoolClassId(1L)).thenReturn(List.of(avecSalle, sansSalle));

            List<ScheduleResponse> responses = service.listByClass(1L);

            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).getClassId()).isEqualTo(1L);
            assertThat(responses.get(0).getClassName()).isEqualTo("6e A");
            assertThat(responses.get(0).getTeacherName()).isEqualTo("Jean Kamdem");
            assertThat(responses.get(0).getRoomName()).isEqualTo("Salle 9");
            assertThat(responses.get(1).getRoomId()).isNull();
            assertThat(responses.get(1).getRoomName()).isNull();
        }
    }

    // ==================================================================
    // 6. Delegation de hasConflict
    // ==================================================================

    @Test
    void hasConflict_transmetLesSeptArgumentsDansLeBonOrdre() {
        // Sept parametres positionnels de meme forme (Long, Long, Long, Long) : une permutation
        // classe/enseignant/salle compilerait et resterait silencieuse.
        ScheduleRepository delegate = mock(ScheduleRepository.class);
        ScheduleService isole = new ScheduleService(delegate, classService, subjectService,
                teacherService, auditService);
        when(delegate.isConflict(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);

        boolean resultat = isole.hasConflict(DayOfWeek.TUESDAY, LocalTime.of(8, 0),
                LocalTime.of(9, 30), 11L, 22L, 33L, 44L);

        assertThat(resultat).isTrue();
        verify(delegate).isConflict(DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(9, 30),
                11L, 22L, 33L, 44L);
    }
}
