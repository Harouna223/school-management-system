package com.school.service;

import com.school.dto.request.TeacherHourlyRateRequest;
import com.school.dto.request.TeacherWorkHourRequest;
import com.school.dto.response.PageResponse;
import com.school.dto.response.TeacherHourlyRateResponse;
import com.school.dto.response.TeacherWorkHourResponse;
import com.school.entity.AcademicYear;
import com.school.entity.Role;
import com.school.entity.SchoolClass;
import com.school.entity.Subject;
import com.school.entity.Teacher;
import com.school.entity.TeacherHourlyRate;
import com.school.entity.TeacherWorkHour;
import com.school.entity.User;
import com.school.enums.ContractType;
import com.school.enums.Gender;
import com.school.enums.TeacherStatus;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.AcademicYearRepository;
import com.school.repository.SchoolClassRepository;
import com.school.repository.SubjectRepository;
import com.school.repository.TeacherHourlyRateRepository;
import com.school.repository.TeacherMonthClosureRepository;
import com.school.repository.TeacherRepository;
import com.school.repository.TeacherWorkHourRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Module « heures enseignees » : tarifs horaires historises, saisie quotidienne, cloture mensuelle.
 *
 * C'est la <b>base du calcul de paie</b>. Les trois regles annoncees en javadoc sont ici epinglees,
 * parce qu'aucune ne casse la compilation et qu'une erreur paie un enseignant de travers :
 * <ol>
 *   <li><b>Le tarif est fige a la saisie</b> ({@code hourlyRateApplied}) — changer un tarif ne doit
 *       JAMAIS recalculer les mois deja saisis ;</li>
 *   <li><b>Le montant est calcule cote serveur</b> ({@code heures x tarif}) et jamais accepte du client ;</li>
 *   <li><b>Doublon interdit</b> (enseignant + date + matiere + classe) et <b>mois cloturé non modifiable</b>
 *       (sauf SUPER_ADMIN / DIRECTEUR).</li>
 * </ol>
 *
 * <p><b>Asymetrie a ne pas « unifier » :</b> {@code record()} passe par {@code assertMonthOpen()},
 * qui n'accorde <b>aucune</b> exemption ; {@code update()} et {@code delete()} passent par
 * {@code assertEditable()}, qui en accorde une a SUPER_ADMIN / DIRECTEUR. Ce n'est pas un oubli :
 * on rouvre pour corriger, pas pour empiler de nouvelles lignes. {@code recordRefuseUnMoisCloture}
 * epingle ce comportement avec un SUPER_ADMIN justement pour qu'une factorisation bien intentionnee
 * des deux methodes soit vue echouer.
 *
 * <p><b>Controles negatifs effectues</b> (copie de travail, jamais le depot) :
 * tarif fige dans {@code update} neutralise =&gt; 1 echec ({@code updateRedetermineLeTarif}) ;
 * plafond de 24 h desactive =&gt; 1 echec ({@code recordRefusePlusDe24hParJour}) ;
 * {@code .filter(existing -> !existing.getId().equals(id))} supprime =&gt; 1 erreur
 * ({@code updateIgnoreSaPropreSaisie}) ; {@code assertMonthOpen} neutralise =&gt; 1 echec
 * ({@code recordRefuseUnMoisCloture}). Les gardes ne sont donc pas decoratives.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeacherHoursServiceTest {

    @Mock
    private TeacherHourlyRateRepository rateRepository;

    @Mock
    private TeacherWorkHourRepository workHourRepository;

    @Mock
    private TeacherMonthClosureRepository closureRepository;

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private SchoolClassRepository schoolClassRepository;

    @Mock
    private AcademicYearRepository academicYearRepository;

    @Mock
    private TeacherService teacherService;

    @Mock
    private AuditService auditService;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private TeacherHoursService teacherHoursService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Jour de travail de reference.
     * ⚠️ Derive de l'horloge, jamais d'une date figee : {@code TeacherWorkHour.validate()}
     * refuse toute date future, donc un {@code LocalDate.of(2026, 10, 5)} ecrit aujourd'hui
     * deviendrait faux demain puis passerait... ou l'inverse. On reste 10 jours dans le passe,
     * ce qui laisse {@code recordRefuseUneDateFuture} tester le futur avec {@code plusDays(1)}.
     */
    private final LocalDate jour = LocalDate.now().minusDays(10);

    private final LocalDate moisDeJour = jour.withDayOfMonth(1);

    private Teacher enseignant(Long id) {
        Teacher t = Teacher.builder()
                .id(id)
                .employeeNo("ENS-2026-0001")
                .firstName("Jean")
                .lastName("Kamdem")
                .gender(Gender.MALE)
                .hireDate(LocalDate.of(2026, 1, 1))
                .contractType(ContractType.CDI)
                .salary(new BigDecimal("250000.00"))
                .status(TeacherStatus.ACTIVE)
                .build();
        when(teacherService.findById(id)).thenReturn(t);
        return t;
    }

    private TeacherHourlyRate tarif(Long id, Teacher t, String montant, LocalDate debut, LocalDate fin) {
        return TeacherHourlyRate.builder()
                .id(id)
                .teacher(t)
                .hourlyRate(new BigDecimal(montant))
                .startDate(debut)
                .endDate(fin)
                .active(true)
                .build();
    }

    private Subject matiere(Long id) {
        return Subject.builder().id(id).name("Mathematiques").code("MATH").build();
    }

    private SchoolClass classe(Long id) {
        return SchoolClass.builder().id(id).name("6eme A").build();
    }

    private TeacherWorkHourRequest saisie(Teacher t, LocalDate date, String heures) {
        return TeacherWorkHourRequest.builder()
                .teacherId(t.getId())
                .date(date)
                .hours(new BigDecimal(heures))
                .build();
    }

    /** Aucune cloture sauf mention contraire ; aucun doublon ; 0 h deja saisies. */
    private void contexteNeutre() {
        when(closureRepository.existsByMonthDateAndClosedTrue(any())).thenReturn(false);
        when(workHourRepository.findByTeacherIdAndDateAndSubjectIdAndSchoolClassId(
                anyLong(), any(), anyLong(), anyLong())).thenReturn(Optional.empty());
        when(workHourRepository.sumHoursOfTeacherOnDate(anyLong(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(workHourRepository.save(any(TeacherWorkHour.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void connecte(User utilisateur) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(utilisateur, null, List.of()));
    }

    /** Utilisateur porteur du role donne (« SUPER_ADMIN », « DIRECTEUR », « SECRETAIRE »…). */
    private User utilisateurAvecRole(Long id, String role) {
        User u = User.builder().id(id).username("u" + id).build();
        u.getRoles().add(Role.builder().id(1L).name(role).build());
        return u;
    }

    // -------------------------------------------------------------- resolveRate

    @Test
    @DisplayName("resolveRate renvoie le tarif applicable a la date")
    void resolveRateRenvoieLeTarifApplicable() {
        Teacher t = enseignant(1L);
        when(rateRepository.findApplicable(1L, jour))
                .thenReturn(List.of(tarif(5L, t, "5000.00", LocalDate.of(2026, 1, 1), null)));

        BigDecimal tarif = teacherHoursService.resolveRate(1L, jour);

        assertThat(tarif).isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("resolveRate prend le PREMIER tarif renvoye (le plus recent en premier)")
    void resolveRatePrendLePlusRecent() {
        Teacher t = enseignant(1L);
        // Le repository trie deja par startDate DESC : on prend la tete de liste.
        when(rateRepository.findApplicable(1L, jour)).thenReturn(List.of(
                tarif(6L, t, "7000.00", LocalDate.of(2026, 6, 1), null),
                tarif(5L, t, "4000.00", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 31))));

        assertThat(teacherHoursService.resolveRate(1L, jour)).isEqualByComparingTo("7000.00");
    }

    @Test
    @DisplayName("resolveRate refuse quand aucun tarif ne couvre la date, et dit quoi faire")
    void resolveRateRefuseSansTarif() {
        when(rateRepository.findApplicable(1L, jour)).thenReturn(List.of());

        assertThatThrownBy(() -> teacherHoursService.resolveRate(1L, jour))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Aucun tarif horaire actif")
                // Le message doit indiquer OU le configurer : sinon l'utilisateur est bloque.
                .hasMessageContaining("Tarifs");
    }

    @Test
    @DisplayName("resolveRate ne renvoie jamais null : une liste vide est une erreur, pas un tarif nul")
    void resolveRateNeRenvoieJamaisNull() {
        when(rateRepository.findApplicable(1L, jour)).thenReturn(List.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> teacherHoursService.resolveRate(1L, jour))
                .isInstanceOf(BusinessException.class);
    }

    // -------------------------------------------------------------- createRate

    @Test
    @DisplayName("createRate force active=true et journalise le tarif et la date de debut")
    void createRateForceActifEtJournalise() {
        Teacher t = enseignant(1L);
        when(academicYearRepository.findByCurrentTrue()).thenReturn(Optional.empty());
        when(rateRepository.existsByTeacherIdAndStartDate(1L, LocalDate.of(2026, 1, 1))).thenReturn(false);
        when(rateRepository.save(any(TeacherHourlyRate.class))).thenAnswer(inv -> {
            TeacherHourlyRate r = inv.getArgument(0);
            r.setId(5L);
            return r;
        });
        TeacherHourlyRateRequest requete = TeacherHourlyRateRequest.builder()
                .teacherId(1L).hourlyRate(new BigDecimal("5000.00"))
                .startDate(LocalDate.of(2026, 1, 1)).build();

        ArgumentCaptor<TeacherHourlyRate> captor = ArgumentCaptor.forClass(TeacherHourlyRate.class);
        TeacherHourlyRateResponse reponse = teacherHoursService.createRate(requete, httpRequest);
        verify(rateRepository).save(captor.capture());

        assertThat(captor.getValue().isActive()).isTrue();
        assertThat(reponse.getHourlyRate()).isEqualByComparingTo("5000.00");
        verify(auditService).log(eq("RATE_CREATE"), eq("TeacherHourlyRate"), eq(5L),
                contains("Jean Kamdem"), eq(httpRequest));
    }

    @Test
    @DisplayName("createRate refuse un doublon (meme enseignant, meme date de debut)")
    void createRateRefuseUnDoublon() {
        enseignant(1L);
        when(academicYearRepository.findByCurrentTrue()).thenReturn(Optional.empty());
        when(rateRepository.existsByTeacherIdAndStartDate(1L, LocalDate.of(2026, 1, 1))).thenReturn(true);
        TeacherHourlyRateRequest requete = TeacherHourlyRateRequest.builder()
                .teacherId(1L).hourlyRate(new BigDecimal("5000.00"))
                .startDate(LocalDate.of(2026, 1, 1)).build();

        assertThatThrownBy(() -> teacherHoursService.createRate(requete, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("existe déjà");

        // Un doublon de tarif rendrait le tarif applique indetermine.
        verify(rateRepository, never()).save(any(TeacherHourlyRate.class));
    }

    @Test
    @DisplayName("createRate refuse un tarif nul ou negatif (validateDates)")
    void createRateRefuseUnTarifNonPositif() {
        enseignant(1L);
        when(academicYearRepository.findByCurrentTrue()).thenReturn(Optional.empty());
        TeacherHourlyRateRequest requete = TeacherHourlyRateRequest.builder()
                .teacherId(1L).hourlyRate(BigDecimal.ZERO)
                .startDate(LocalDate.of(2026, 1, 1)).build();

        assertThatThrownBy(() -> teacherHoursService.createRate(requete, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("strictement positif");

        verify(rateRepository, never()).save(any(TeacherHourlyRate.class));
    }

    @Test
    @DisplayName("createRate refuse une date de fin anterieure a la date de debut")
    void createRateRefuseUnePeriodeInversee() {
        enseignant(1L);
        when(academicYearRepository.findByCurrentTrue()).thenReturn(Optional.empty());
        TeacherHourlyRateRequest requete = TeacherHourlyRateRequest.builder()
                .teacherId(1L).hourlyRate(new BigDecimal("5000.00"))
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 1, 1)).build();

        assertThatThrownBy(() -> teacherHoursService.createRate(requete, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("postérieure");
    }

    @Test
    @DisplayName("createRate resout l'annee scolaire : explicite, sinon l'annee courante")
    void createRateResoutLAnneeScolaire() {
        enseignant(1L);
        AcademicYear annee = AcademicYear.builder().id(2L).label("2026-2027")
                .startDate(LocalDate.of(2026, 9, 1)).endDate(LocalDate.of(2027, 7, 31)).build();
        when(academicYearRepository.findById(2L)).thenReturn(Optional.of(annee));
        when(rateRepository.existsByTeacherIdAndStartDate(anyLong(), any())).thenReturn(false);
        when(rateRepository.save(any(TeacherHourlyRate.class))).thenAnswer(inv -> inv.getArgument(0));
        TeacherHourlyRateRequest requete = TeacherHourlyRateRequest.builder()
                .teacherId(1L).hourlyRate(new BigDecimal("5000.00"))
                .startDate(LocalDate.of(2026, 9, 1)).academicYearId(2L).build();

        ArgumentCaptor<TeacherHourlyRate> captor = ArgumentCaptor.forClass(TeacherHourlyRate.class);
        teacherHoursService.createRate(requete, httpRequest);
        verify(rateRepository).save(captor.capture());

        assertThat(captor.getValue().getAcademicYear().getId()).isEqualTo(2L);
        // L'annee explicite est resolue sans interroger l'annee courante.
        verify(academicYearRepository, never()).findByCurrentTrue();
    }

    @Test
    @DisplayName("createRate avec une annee inconnue leve une exception")
    void createRateAvecAnneeInconnueLeveUneException() {
        enseignant(1L);
        when(academicYearRepository.findById(404L)).thenReturn(Optional.empty());
        TeacherHourlyRateRequest requete = TeacherHourlyRateRequest.builder()
                .teacherId(1L).hourlyRate(new BigDecimal("5000.00"))
                .startDate(LocalDate.of(2026, 1, 1)).academicYearId(404L).build();

        assertThatThrownBy(() -> teacherHoursService.createRate(requete, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Année scolaire");
    }

    // -------------------------------------------------------------- toggleRate

    @Test
    @DisplayName("toggleRate desactive un tarif sans le supprimer (les heures passees le conservent)")
    void toggleRateDesactiveSansSupprimer() {
        Teacher t = enseignant(1L);
        TeacherHourlyRate existant = tarif(5L, t, "5000.00", LocalDate.of(2026, 1, 1), null);
        when(rateRepository.findById(5L)).thenReturn(Optional.of(existant));
        when(rateRepository.save(any(TeacherHourlyRate.class))).thenAnswer(inv -> inv.getArgument(0));

        TeacherHourlyRateResponse reponse = teacherHoursService.toggleRate(5L, false, httpRequest);

        assertThat(reponse.isActive()).isFalse();
        verify(rateRepository).save(existant);
        // La desactivation remplace la suppression : historique preserve.
        verify(rateRepository, never()).delete(any(TeacherHourlyRate.class));
        verify(auditService).log(eq("RATE_STATUS"), eq("TeacherHourlyRate"), eq(5L),
                contains("Désactivation"), eq(httpRequest));
    }

    @Test
    @DisplayName("toggleRate peut reactiver un tarif")
    void toggleRatePeutReactiver() {
        Teacher t = enseignant(1L);
        TeacherHourlyRate existant = tarif(5L, t, "5000.00", LocalDate.of(2026, 1, 1), null);
        existant.setActive(false);
        when(rateRepository.findById(5L)).thenReturn(Optional.of(existant));
        when(rateRepository.save(any(TeacherHourlyRate.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(teacherHoursService.toggleRate(5L, true, httpRequest).isActive()).isTrue();
    }

    @Test
    @DisplayName("toggleRate sur un tarif inconnu leve une exception nommee")
    void toggleRateSurUnTarifInconnuLeveUneException() {
        when(rateRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherHoursService.toggleRate(404L, true, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tarif horaire");
    }

    // ------------------------------------------------------------------ record

    @Test
    @DisplayName("record fige le tarif applique et calcule le montant (heures x tarif)")
    void recordFigeLeTarifEtCalculeLeMontant() {
        Teacher t = enseignant(1L);
        when(rateRepository.findApplicable(1L, jour))
                .thenReturn(List.of(tarif(5L, t, "5000.00", LocalDate.of(2026, 1, 1), null)));
        contexteNeutre();

        ArgumentCaptor<TeacherWorkHour> captor = ArgumentCaptor.forClass(TeacherWorkHour.class);
        teacherHoursService.record(saisie(t, jour, "3"), httpRequest);
        verify(workHourRepository).save(captor.capture());

        assertThat(captor.getValue().getHourlyRateApplied()).isEqualByComparingTo("5000.00");
        // Le montant est calcule dans @PrePersist (recalculate) : on l'appelle ici comme le ferait JPA.
        TeacherWorkHour w = captor.getValue();
        w.prePersist();
        assertThat(w.getAmount()).isEqualByComparingTo("15000.00");
    }

    @Test
    @DisplayName("record n'accepte JAMAIS un montant du client (il n'y a pas de champ montant)")
    void recordIgnoreToutMontantClient() {
        Teacher t = enseignant(1L);
        when(rateRepository.findApplicable(1L, jour))
                .thenReturn(List.of(tarif(5L, t, "5000.00", LocalDate.of(2026, 1, 1), null)));
        contexteNeutre();

        ArgumentCaptor<TeacherWorkHour> captor = ArgumentCaptor.forClass(TeacherWorkHour.class);
        // Le DTO n'expose que `hours` : impossible d'injecter un montant, on le verifie quand meme.
        teacherHoursService.record(saisie(t, jour, "3"), httpRequest);
        verify(workHourRepository).save(captor.capture());

        assertThat(captor.getValue().getHours()).isEqualByComparingTo("3");
        assertThat(captor.getValue().getAmount()).isNull(); // calcule seulement au flush
    }

    @Test
    @DisplayName("record refuse un doublon (enseignant + date + matiere + classe)")
    void recordRefuseUnDoublon() {
        Teacher t = enseignant(1L);
        when(rateRepository.findApplicable(1L, jour))
                .thenReturn(List.of(tarif(5L, t, "5000.00", LocalDate.of(2026, 1, 1), null)));
        when(closureRepository.existsByMonthDateAndClosedTrue(any())).thenReturn(false);
        when(workHourRepository.findByTeacherIdAndDateAndSubjectIdAndSchoolClassId(
                1L, jour, -1L, -1L)).thenReturn(Optional.of(
                        TeacherWorkHour.builder().id(77L).teacher(t).date(jour).build()));

        assertThatThrownBy(() -> teacherHoursService.record(saisie(t, jour, "3"), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("existe déjà");

        verify(workHourRepository, never()).save(any(TeacherWorkHour.class));
    }

    @Test
    @DisplayName("record utilise -1 comme cle pour une matiere/classe absente (pas null)")
    void recordUtiliseMoinsUnPourLesClesAbsentes() {
        Teacher t = enseignant(1L);
        when(rateRepository.findApplicable(1L, jour))
                .thenReturn(List.of(tarif(5L, t, "5000.00", LocalDate.of(2026, 1, 1), null)));
        contexteNeutre();

        teacherHoursService.record(saisie(t, jour, "3"), httpRequest);

        // Sans matiere ni classe, la cle de doublon doit rester stable et comparable en base.
        verify(workHourRepository).findByTeacherIdAndDateAndSubjectIdAndSchoolClassId(1L, jour, -1L, -1L);
    }

    @Test
    @DisplayName("record refuse un total journalier superieur a 24 h")
    void recordRefusePlusDe24hParJour() {
        Teacher t = enseignant(1L);
        when(rateRepository.findApplicable(1L, jour))
                .thenReturn(List.of(tarif(5L, t, "5000.00", LocalDate.of(2026, 1, 1), null)));
        when(closureRepository.existsByMonthDateAndClosedTrue(any())).thenReturn(false);
        when(workHourRepository.findByTeacherIdAndDateAndSubjectIdAndSchoolClassId(
                anyLong(), any(), anyLong(), anyLong())).thenReturn(Optional.empty());
        // 22 h deja saisies + 3 h = 25 h > 24 h.
        when(workHourRepository.sumHoursOfTeacherOnDate(1L, jour)).thenReturn(new BigDecimal("22"));

        assertThatThrownBy(() -> teacherHoursService.record(saisie(t, jour, "3"), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("24 h");

        verify(workHourRepository, never()).save(any(TeacherWorkHour.class));
    }

    @Test
    @DisplayName("record accepte un total journalier de exactement 24 h")
    void recordAccepteExactement24h() {
        Teacher t = enseignant(1L);
        when(rateRepository.findApplicable(1L, jour))
                .thenReturn(List.of(tarif(5L, t, "5000.00", LocalDate.of(2026, 1, 1), null)));
        contexteNeutre();
        when(workHourRepository.sumHoursOfTeacherOnDate(1L, jour)).thenReturn(new BigDecimal("21"));

        // 21 + 3 = 24 : la borne est inclusive (« ne peut pas depasser 24 h »).
        TeacherWorkHourResponse reponse = teacherHoursService.record(saisie(t, jour, "3"), httpRequest);

        assertThat(reponse.getHours()).isEqualByComparingTo("3");
        verify(workHourRepository).save(any(TeacherWorkHour.class));
    }

    @Test
    @DisplayName("record refuse une date future")
    void recordRefuseUneDateFuture() {
        Teacher t = enseignant(1L);
        LocalDate demain = LocalDate.now().plusDays(1);
        when(rateRepository.findApplicable(1L, demain))
                .thenReturn(List.of(tarif(5L, t, "5000.00", LocalDate.of(2026, 1, 1), null)));
        when(closureRepository.existsByMonthDateAndClosedTrue(any())).thenReturn(false);
        when(workHourRepository.findByTeacherIdAndDateAndSubjectIdAndSchoolClassId(
                anyLong(), any(), anyLong(), anyLong())).thenReturn(Optional.empty());
        when(workHourRepository.sumHoursOfTeacherOnDate(anyLong(), any())).thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> teacherHoursService.record(saisie(t, demain, "3"), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("date future");

        verify(workHourRepository, never()).save(any(TeacherWorkHour.class));
    }

    @Test
    @DisplayName("record refuse un mois cloturé, meme pour un administrateur")
    void recordRefuseUnMoisCloture() {
        Teacher t = enseignant(1L);
        // ⚠️ Mois CLOTURE volontairement dans le PASSE : sans cela, la date de saisie serait
        // future et `TeacherWorkHour.validate()` lancerait "date future" au lieu de "clôturé",
        // et le test passerait pour la mauvaise raison.
        LocalDate moisPasse = jour.minusMonths(2).withDayOfMonth(1);
        when(rateRepository.findApplicable(anyLong(), any()))
                .thenReturn(List.of(tarif(5L, t, "5000.00", LocalDate.of(2026, 1, 1), null)));
        when(closureRepository.existsByMonthDateAndClosedTrue(moisPasse)).thenReturn(true);
        // Un SUPER_ADMIN : la creation d'heures n'a PAS d'exemption, contrairement a assertEditable.
        connecte(utilisateurAvecRole(1L, "SUPER_ADMIN"));

        assertThatThrownBy(() -> teacherHoursService.record(
                saisie(t, moisPasse.plusDays(4), "3"), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("clôturé");

        verify(workHourRepository, never()).save(any(TeacherWorkHour.class));
    }

    @Test
    @DisplayName("record rattache createdBy a l'utilisateur authentifie")
    void recordRattacheCreatedBy() {
        Teacher t = enseignant(1L);
        when(rateRepository.findApplicable(1L, jour))
                .thenReturn(List.of(tarif(5L, t, "5000.00", LocalDate.of(2026, 1, 1), null)));
        contexteNeutre();
        connecte(User.builder().id(4L).username("secretaire").build());

        ArgumentCaptor<TeacherWorkHour> captor = ArgumentCaptor.forClass(TeacherWorkHour.class);
        teacherHoursService.record(saisie(t, jour, "3"), httpRequest);
        verify(workHourRepository).save(captor.capture());

        assertThat(captor.getValue().getCreatedBy().getId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("record resout la matiere et la classe quand elles sont fournies")
    void recordResoutMatiereEtClasse() {
        Teacher t = enseignant(1L);
        when(rateRepository.findApplicable(1L, jour))
                .thenReturn(List.of(tarif(5L, t, "5000.00", LocalDate.of(2026, 1, 1), null)));
        contexteNeutre();
        when(subjectRepository.findById(3L)).thenReturn(Optional.of(matiere(3L)));
        when(schoolClassRepository.findById(8L)).thenReturn(Optional.of(classe(8L)));
        TeacherWorkHourRequest requete = TeacherWorkHourRequest.builder()
                .teacherId(1L).date(jour).hours(new BigDecimal("3"))
                .subjectId(3L).classId(8L).build();

        ArgumentCaptor<TeacherWorkHour> captor = ArgumentCaptor.forClass(TeacherWorkHour.class);
        TeacherWorkHourResponse reponse = teacherHoursService.record(requete, httpRequest);
        verify(workHourRepository).save(captor.capture());

        assertThat(captor.getValue().getSubject().getId()).isEqualTo(3L);
        assertThat(captor.getValue().getSchoolClass().getId()).isEqualTo(8L);
        assertThat(reponse.getSubjectName()).isEqualTo("Mathematiques");
        assertThat(reponse.getClassName()).isEqualTo("6eme A");
    }

    @Test
    @DisplayName("record avec une matiere inconnue leve une exception")
    void recordAvecMatiereInconnueLeveUneException() {
        Teacher t = enseignant(1L);
        when(rateRepository.findApplicable(1L, jour))
                .thenReturn(List.of(tarif(5L, t, "5000.00", LocalDate.of(2026, 1, 1), null)));
        contexteNeutre();
        when(subjectRepository.findById(404L)).thenReturn(Optional.empty());
        TeacherWorkHourRequest requete = TeacherWorkHourRequest.builder()
                .teacherId(1L).date(jour).hours(new BigDecimal("3")).subjectId(404L).build();

        assertThatThrownBy(() -> teacherHoursService.record(requete, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Matière");
    }

    @Test
    @DisplayName("record avec une classe inconnue leve une exception")
    void recordAvecClasseInconnueLeveUneException() {
        Teacher t = enseignant(1L);
        when(rateRepository.findApplicable(1L, jour))
                .thenReturn(List.of(tarif(5L, t, "5000.00", LocalDate.of(2026, 1, 1), null)));
        contexteNeutre();
        when(schoolClassRepository.findById(404L)).thenReturn(Optional.empty());
        TeacherWorkHourRequest requete = TeacherWorkHourRequest.builder()
                .teacherId(1L).date(jour).hours(new BigDecimal("3")).classId(404L).build();

        assertThatThrownBy(() -> teacherHoursService.record(requete, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Classe");
    }

    @Test
    @DisplayName("record journalise les heures et le tarif applique")
    void recordJournalise() {
        Teacher t = enseignant(1L);
        when(rateRepository.findApplicable(1L, jour))
                .thenReturn(List.of(tarif(5L, t, "5000.00", LocalDate.of(2026, 1, 1), null)));
        contexteNeutre();
        when(workHourRepository.save(any(TeacherWorkHour.class))).thenAnswer(inv -> {
            TeacherWorkHour w = inv.getArgument(0);
            w.setId(88L);
            return w;
        });

        teacherHoursService.record(saisie(t, jour, "3"), httpRequest);

        verify(auditService).log(eq("HOURS_RECORD"), eq("TeacherWorkHour"), eq(88L),
                contains("Jean Kamdem"), eq(httpRequest));
    }

    // ------------------------------------------------------------------ update

    @Test
    @DisplayName("update re-determine le tarif depuis la NOUVELLE date (le montant est recalcule)")
    void updateRedetermineLeTarif() {
        Teacher t = enseignant(1L);
        TeacherWorkHour existant = TeacherWorkHour.builder()
                .id(77L).teacher(t).date(LocalDate.of(2026, 3, 10))
                .hours(new BigDecimal("2")).hourlyRateApplied(new BigDecimal("4000.00"))
                .build();
        when(workHourRepository.findById(77L)).thenReturn(Optional.of(existant));
        when(closureRepository.existsByMonthDateAndClosedTrue(any())).thenReturn(false);
        when(workHourRepository.findByTeacherIdAndDateAndSubjectIdAndSchoolClassId(
                anyLong(), any(), anyLong(), anyLong())).thenReturn(Optional.empty());
        // La nouvelle date (octobre) tombe sous un tarif plus eleve.
        when(rateRepository.findApplicable(1L, jour))
                .thenReturn(List.of(tarif(6L, t, "7000.00", LocalDate.of(2026, 6, 1), null)));
        when(workHourRepository.save(any(TeacherWorkHour.class))).thenAnswer(inv -> inv.getArgument(0));

        TeacherWorkHourResponse reponse = teacherHoursService.update(77L, saisie(t, jour, "4"), httpRequest);

        assertThat(reponse.getHourlyRateApplied()).isEqualByComparingTo("7000.00");
        assertThat(reponse.getDate()).isEqualTo(jour);
        assertThat(reponse.getHours()).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("update refuse si le mois de la saisie existante est cloturé et l'utilisateur non admin")
    void updateRefuseUnMoisCloture() {
        Teacher t = enseignant(1L);
        TeacherWorkHour existant = TeacherWorkHour.builder()
                .id(77L).teacher(t).date(jour).hours(new BigDecimal("2"))
                .hourlyRateApplied(new BigDecimal("4000.00")).build();
        when(workHourRepository.findById(77L)).thenReturn(Optional.of(existant));
        when(closureRepository.existsByMonthDateAndClosedTrue(moisDeJour)).thenReturn(true);
        connecte(utilisateurAvecRole(4L, "SECRETAIRE"));

        assertThatThrownBy(() -> teacherHoursService.update(77L, saisie(t, jour, "4"), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("clôturé");
    }

    @Test
    @DisplayName("update AUTORISE un SUPER_ADMIN a modifier un mois cloturé")
    void updateAutoriseUnSuperAdmin() {
        Teacher t = enseignant(1L);
        TeacherWorkHour existant = TeacherWorkHour.builder()
                .id(77L).teacher(t).date(jour).hours(new BigDecimal("2"))
                .hourlyRateApplied(new BigDecimal("4000.00")).build();
        when(workHourRepository.findById(77L)).thenReturn(Optional.of(existant));
        when(closureRepository.existsByMonthDateAndClosedTrue(moisDeJour)).thenReturn(true);
        when(workHourRepository.findByTeacherIdAndDateAndSubjectIdAndSchoolClassId(
                anyLong(), any(), anyLong(), anyLong())).thenReturn(Optional.empty());
        when(rateRepository.findApplicable(1L, jour))
                .thenReturn(List.of(tarif(5L, t, "5000.00", LocalDate.of(2026, 1, 1), null)));
        when(workHourRepository.save(any(TeacherWorkHour.class))).thenAnswer(inv -> inv.getArgument(0));
        connecte(utilisateurAvecRole(1L, "SUPER_ADMIN"));

        TeacherWorkHourResponse reponse = teacherHoursService.update(77L, saisie(t, jour, "4"), httpRequest);

        assertThat(reponse.getHours()).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("update AUTORISE un DIRECTEUR a modifier un mois cloturé")
    void updateAutoriseUnDirecteur() {
        Teacher t = enseignant(1L);
        TeacherWorkHour existant = TeacherWorkHour.builder()
                .id(77L).teacher(t).date(jour).hours(new BigDecimal("2"))
                .hourlyRateApplied(new BigDecimal("4000.00")).build();
        when(workHourRepository.findById(77L)).thenReturn(Optional.of(existant));
        when(closureRepository.existsByMonthDateAndClosedTrue(any())).thenReturn(true);
        when(workHourRepository.findByTeacherIdAndDateAndSubjectIdAndSchoolClassId(
                anyLong(), any(), anyLong(), anyLong())).thenReturn(Optional.empty());
        when(rateRepository.findApplicable(1L, jour))
                .thenReturn(List.of(tarif(5L, t, "5000.00", LocalDate.of(2026, 1, 1), null)));
        when(workHourRepository.save(any(TeacherWorkHour.class))).thenAnswer(inv -> inv.getArgument(0));
        connecte(utilisateurAvecRole(1L, "DIRECTEUR"));

        assertThat(teacherHoursService.update(77L, saisie(t, jour, "4"), httpRequest).getHours())
                .isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("update IGNORE la saisie elle-meme dans la detection de doublon")
    void updateIgnoreSaPropreSaisie() {
        Teacher t = enseignant(1L);
        TeacherWorkHour existant = TeacherWorkHour.builder()
                .id(77L).teacher(t).date(jour).hours(new BigDecimal("2"))
                .hourlyRateApplied(new BigDecimal("4000.00")).build();
        when(workHourRepository.findById(77L)).thenReturn(Optional.of(existant));
        when(closureRepository.existsByMonthDateAndClosedTrue(any())).thenReturn(false);
        // Le repository renvoie la saisie en cours de modification : elle ne doit pas compter comme doublon.
        when(workHourRepository.findByTeacherIdAndDateAndSubjectIdAndSchoolClassId(
                anyLong(), any(), anyLong(), anyLong())).thenReturn(Optional.of(existant));
        when(rateRepository.findApplicable(1L, jour))
                .thenReturn(List.of(tarif(5L, t, "5000.00", LocalDate.of(2026, 1, 1), null)));
        when(workHourRepository.save(any(TeacherWorkHour.class))).thenAnswer(inv -> inv.getArgument(0));

        TeacherWorkHourResponse reponse = teacherHoursService.update(77L, saisie(t, jour, "4"), httpRequest);

        // Sans le `.filter(existing -> !existing.getId().equals(id))`, toute correction echouerait.
        assertThat(reponse.getHours()).isEqualByComparingTo("4");
        verify(workHourRepository).save(any(TeacherWorkHour.class));
    }

    @Test
    @DisplayName("update refuse un doublon avec une AUTRE saisie")
    void updateRefuseUnDoublonAvecUneAutreSaisie() {
        Teacher t = enseignant(1L);
        TeacherWorkHour existant = TeacherWorkHour.builder()
                .id(77L).teacher(t).date(jour).hours(new BigDecimal("2"))
                .hourlyRateApplied(new BigDecimal("4000.00")).build();
        TeacherWorkHour autre = TeacherWorkHour.builder()
                .id(99L).teacher(t).date(jour).hours(new BigDecimal("5")).build();
        when(workHourRepository.findById(77L)).thenReturn(Optional.of(existant));
        when(closureRepository.existsByMonthDateAndClosedTrue(any())).thenReturn(false);
        when(workHourRepository.findByTeacherIdAndDateAndSubjectIdAndSchoolClassId(
                anyLong(), any(), anyLong(), anyLong())).thenReturn(Optional.of(autre));

        assertThatThrownBy(() -> teacherHoursService.update(77L, saisie(t, jour, "4"), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("autre saisie");

        verify(workHourRepository, never()).save(any(TeacherWorkHour.class));
    }

    @Test
    @DisplayName("update sur une saisie inconnue leve une exception nommee")
    void updateSurUneSaisieInconnueLeveUneException() {
        when(workHourRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherHoursService.update(404L, saisie(enseignant(1L), jour, "4"), httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Saisie d'heures");
    }

    // ------------------------------------------------------------------ delete

    @Test
    @DisplayName("delete supprime une saisie d'un mois ouvert et journalise")
    void deleteSupprimeEtJournalise() {
        Teacher t = enseignant(1L);
        TeacherWorkHour existant = TeacherWorkHour.builder()
                .id(77L).teacher(t).date(jour).hours(new BigDecimal("2")).build();
        when(workHourRepository.findById(77L)).thenReturn(Optional.of(existant));
        when(closureRepository.existsByMonthDateAndClosedTrue(any())).thenReturn(false);

        teacherHoursService.delete(77L, httpRequest);

        verify(workHourRepository).delete(existant);
        verify(auditService).log(eq("HOURS_DELETE"), eq("TeacherWorkHour"), eq(77L),
                contains("Jean Kamdem"), eq(httpRequest));
    }

    @Test
    @DisplayName("delete refuse un mois cloturé pour un non-admin")
    void deleteRefuseUnMoisCloture() {
        TeacherWorkHour existant = TeacherWorkHour.builder()
                .id(77L).teacher(enseignant(1L)).date(jour).hours(new BigDecimal("2")).build();
        when(workHourRepository.findById(77L)).thenReturn(Optional.of(existant));
        when(closureRepository.existsByMonthDateAndClosedTrue(moisDeJour)).thenReturn(true);
        connecte(utilisateurAvecRole(4L, "SECRETAIRE"));

        assertThatThrownBy(() -> teacherHoursService.delete(77L, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("clôturé");

        verify(workHourRepository, never()).delete(any(TeacherWorkHour.class));
    }

    @Test
    @DisplayName("delete AUTORISE un SUPER_ADMIN sur un mois cloturé")
    void deleteAutoriseUnSuperAdmin() {
        TeacherWorkHour existant = TeacherWorkHour.builder()
                .id(77L).teacher(enseignant(1L)).date(jour).hours(new BigDecimal("2")).build();
        when(workHourRepository.findById(77L)).thenReturn(Optional.of(existant));
        when(closureRepository.existsByMonthDateAndClosedTrue(any())).thenReturn(true);
        connecte(utilisateurAvecRole(1L, "SUPER_ADMIN"));

        teacherHoursService.delete(77L, httpRequest);

        verify(workHourRepository).delete(existant);
    }

    @Test
    @DisplayName("delete sur une saisie inconnue leve une exception")
    void deleteSurUneSaisieInconnueLeveUneException() {
        when(workHourRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherHoursService.delete(404L, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------ cloture et lecture

    @Test
    @DisplayName("assertEditable laisse passer un mois ouvert sans meme charger l'utilisateur")
    void assertEditableLaissePasserUnMoisOuvert() {
        when(closureRepository.existsByMonthDateAndClosedTrue(moisDeJour)).thenReturn(false);

        // Aucune exception, et surtout aucun acces au contexte de securite.
        teacherHoursService.assertEditable(jour);

        verify(closureRepository).existsByMonthDateAndClosedTrue(moisDeJour);
    }

    @Test
    @DisplayName("assertEditable normalise la date au premier jour du mois")
    void assertEditableNormaliseLaDate() {
        when(closureRepository.existsByMonthDateAndClosedTrue(any())).thenReturn(false);

        teacherHoursService.assertEditable(jour.withDayOfMonth(jour.lengthOfMonth()));

        // La cloture est mensuelle : n'importe quel jour d'octobre teste le 01/10.
        verify(closureRepository).existsByMonthDateAndClosedTrue(moisDeJour);
    }

    @Test
    @DisplayName("assertEditable refuse un mois cloturé quand personne n'est authentifie")
    void assertEditableRefuseSansUtilisateur() {
        when(closureRepository.existsByMonthDateAndClosedTrue(any())).thenReturn(true);

        assertThatThrownBy(() -> teacherHoursService.assertEditable(jour))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("clôturé");
    }

    @Test
    @DisplayName("isClosed interroge la cloture du mois donne")
    void isClosedInterrogeLaCloture() {
        when(closureRepository.existsByMonthDateAndClosedTrue(moisDeJour)).thenReturn(true);

        assertThat(teacherHoursService.isClosed(moisDeJour)).isTrue();
    }

    @Test
    @DisplayName("search impose le tri par date decroissante puis id decroissant")
    void searchImposeLeTri() {
        Page<TeacherWorkHour> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0L);
        when(workHourRepository.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<TeacherWorkHourResponse> reponse = teacherHoursService.search(
                1L, 3L, 8L, jour.minusDays(30), jour, 2L, 0, 20);

        assertThat(reponse.getContent()).isEmpty();
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(workHourRepository).search(eq(1L), eq(3L), eq(8L), any(), any(), eq(2L), captor.capture());
        // Les saisies les plus recentes d'abord : le tri est impose par le service.
        assertThat(captor.getValue().getSort().getOrderFor("date")).isNotNull();
        assertThat(captor.getValue().getSort().getOrderFor("date").getDirection().isDescending()).isTrue();
    }

    @Test
    @DisplayName("byTeacherAndDate renvoie les saisies du jour de l'enseignant")
    void byTeacherAndDateRenvoieLesSaisies() {
        Teacher t = enseignant(1L);
        when(workHourRepository.findByTeacherIdAndDateOrderBySubjectAsc(1L, jour))
                .thenReturn(List.of(TeacherWorkHour.builder()
                        .id(77L).teacher(t).date(jour).hours(new BigDecimal("3"))
                        .hourlyRateApplied(new BigDecimal("5000.00")).build()));

        List<TeacherWorkHourResponse> reponses = teacherHoursService.byTeacherAndDate(1L, jour);

        assertThat(reponses).hasSize(1);
        assertThat(reponses.get(0).getTeacherId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("listRates transmet les trois filtres et mappe le tarif")
    void listRatesTransmetLesFiltres() {
        Teacher t = enseignant(1L);
        when(rateRepository.search(1L, 2L, true))
                .thenReturn(List.of(tarif(5L, t, "5000.00", LocalDate.of(2026, 1, 1), null)));

        List<TeacherHourlyRateResponse> reponses = teacherHoursService.listRates(1L, 2L, true);

        assertThat(reponses).hasSize(1);
        assertThat(reponses.get(0).getHourlyRate()).isEqualByComparingTo("5000.00");
        verify(rateRepository).search(1L, 2L, true);
    }

    @Test
    @DisplayName("record n'appelle jamais le repository d'heures si le tarif manque")
    void recordSansTarifNecritRien() {
        enseignant(1L);
        when(rateRepository.findApplicable(1L, jour)).thenReturn(List.of());
        when(closureRepository.existsByMonthDateAndClosedTrue(any())).thenReturn(false);

        assertThatThrownBy(() -> teacherHoursService.record(saisie(enseignant(1L), jour, "3"), httpRequest))
                .isInstanceOf(BusinessException.class);

        // Echec tot : rien n'est ecrit.
        verifyNoInteractions(workHourRepository);
    }
}
