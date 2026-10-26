package com.school.service;

import com.school.dto.response.DashboardResponse;
import com.school.dto.response.PageResponse;
import com.school.entity.SchoolClass;
import com.school.enums.AttendanceStatus;
import com.school.enums.BorrowingStatus;
import com.school.enums.EducationCycle;
import com.school.enums.InvoiceStatus;
import com.school.enums.LeaveStatus;
import com.school.enums.StudentStatus;
import com.school.repository.AttendanceRepository;
import com.school.repository.AuditLogRepository;
import com.school.repository.BookRepository;
import com.school.repository.BorrowingRepository;
import com.school.repository.ExpenseRepository;
import com.school.repository.InvoiceRepository;
import com.school.repository.LeaveRepository;
import com.school.repository.PaymentRepository;
import com.school.repository.SchoolClassRepository;
import com.school.repository.StudentRepository;
import com.school.repository.SubjectRepository;
import com.school.repository.TeacherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Agrégation des statistiques du tableau de bord.
 *
 * <p>Ce service n'avait aucun test alors qu'il est le plus consulté de
 * l'application et que chacune de ses valeurs est un <b>calcul silencieux</b> :
 * une somme incomplète, une borne de date trop courte ou une division mal
 * protégée ne lèvent rien — le chiffre est simplement faux à l'écran.</p>
 *
 * <p>Un défaut de cette famille a déjà été corrigé ici : les factures en retard
 * n'étaient pas comptées dans {@code unpaidInvoices}, le compteur tombait à zéro
 * le lendemain du passage automatique en {@code OVERDUE}. Le même oubli subsistait
 * pour {@code borrowedBooks} (constaté en production : 1 emprunt {@code OVERDUE},
 * 0 {@code BORROWED}, tableau de bord annonçant 0 livre emprunté).</p>
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private StudentRepository studentRepository;
    @Mock private TeacherRepository teacherRepository;
    @Mock private SchoolClassRepository classRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private AttendanceRepository attendanceRepository;
    @Mock private BookRepository bookRepository;
    @Mock private BorrowingRepository borrowingRepository;
    @Mock private LeaveRepository leaveRepository;
    @Mock private AuditLogRepository auditLogRepository;

    private DashboardService service;

    private final LocalDate today = LocalDate.now();
    private final YearMonth currentMonth = YearMonth.from(today);

    @BeforeEach
    void setUp() {
        service = new DashboardService(studentRepository, teacherRepository, classRepository,
                subjectRepository, invoiceRepository, paymentRepository, expenseRepository,
                attendanceRepository, bookRepository, borrowingRepository, leaveRepository,
                auditLogRepository);
    }

    private static SchoolClass classe(Long id, String name) {
        return SchoolClass.builder().id(id).name(name).build();
    }

    // =====================================================================
    // Compteurs qui doivent inclure les retards
    // =====================================================================

    @Nested
    @DisplayName("Compteurs incluant les retards")
    class Retards {

        @Test
        @DisplayName("borrowedBooks additionne BORROWED et OVERDUE")
        void borrowedBooksInclutOverdue() {
            /*
             * Le job de nuit (4 h) bascule BORROWED -> OVERDUE. Ne compter que
             * BORROWED remet le compteur à zéro chaque matin, alors que les
             * livres sont toujours sortis.
             */
            when(borrowingRepository.countByStatus(BorrowingStatus.BORROWED)).thenReturn(2L);
            when(borrowingRepository.countByStatus(BorrowingStatus.OVERDUE)).thenReturn(3L);

            assertThat(service.stats().getBorrowedBooks()).isEqualTo(5L);
        }

        @Test
        @DisplayName("unpaidInvoices additionne UNPAID, PARTIAL et OVERDUE")
        void unpaidInvoicesInclutOverdue() {
            when(invoiceRepository.countByStatus(InvoiceStatus.UNPAID)).thenReturn(1L);
            when(invoiceRepository.countByStatus(InvoiceStatus.PARTIAL)).thenReturn(2L);
            when(invoiceRepository.countByStatus(InvoiceStatus.OVERDUE)).thenReturn(4L);

            assertThat(service.stats().getUnpaidInvoices()).isEqualTo(7L);
        }
    }

    // =====================================================================
    // Division par le nombre de classes
    // =====================================================================

    @Nested
    @DisplayName("Moyenne d'élèves par classe")
    class Moyenne {

        @Test
        @DisplayName("vaut 0 quand il n'y a aucun élève, même sans classe")
        void zeroEleveZeroClasse() {
            when(studentRepository.count()).thenReturn(0L);
            when(classRepository.findAll()).thenReturn(List.of());

            assertThat(service.stats().getStudentsPerClassAvg()).isZero();
        }

        @Test
        @DisplayName("vaut le nombre d'élèves quand il n'existe aucune classe")
        void elevesSansClasse() {
            // Sans le Math.max(1, …), ce cas produisait Infinity : un « ∞ »
            // affiché sur la carte du tableau de bord.
            when(studentRepository.count()).thenReturn(10L);
            when(classRepository.findAll()).thenReturn(List.of());

            assertThat(service.stats().getStudentsPerClassAvg()).isEqualTo(10.0);
        }

        @Test
        @DisplayName("divise par le nombre réel de classes")
        void moyenneNormale() {
            when(studentRepository.count()).thenReturn(10L);
            when(classRepository.findAll()).thenReturn(List.of(
                    classe(1L, "6e A"), classe(2L, "6e B"), classe(3L, "5e A"), classe(4L, "5e B")));

            assertThat(service.stats().getStudentsPerClassAvg()).isEqualTo(2.5);
        }
    }

    // =====================================================================
    // Filtre par cycle
    // =====================================================================

    @Nested
    @DisplayName("Filtre par cycle")
    class FiltreCycle {

        @Test
        @DisplayName("sans cycle : compteurs globaux et toutes les classes")
        void sansCycle() {
            when(studentRepository.count()).thenReturn(100L);
            when(studentRepository.countByStatus(StudentStatus.ACTIVE)).thenReturn(80L);
            when(classRepository.findAll()).thenReturn(List.of(classe(1L, "6e A")));

            DashboardResponse response = service.stats();

            assertThat(response.getTotalStudents()).isEqualTo(100L);
            assertThat(response.getActiveStudents()).isEqualTo(80L);
            assertThat(response.getTotalClasses()).isEqualTo(1L);
            verify(studentRepository, never()).countByEducationCycle(any());
            verify(classRepository, never()).findByLevelEducationCycle(any());
        }

        @Test
        @DisplayName("avec cycle : les compteurs par cycle, jamais les globaux")
        void avecCycle() {
            // Le piège : accepter le paramètre mais continuer à compter globalement.
            when(studentRepository.countByEducationCycle(EducationCycle.COLLEGE)).thenReturn(42L);
            when(studentRepository.countByStatusAndEducationCycle(StudentStatus.ACTIVE, EducationCycle.COLLEGE))
                    .thenReturn(30L);
            when(classRepository.findByLevelEducationCycle(EducationCycle.COLLEGE))
                    .thenReturn(List.of(classe(1L, "6e A"), classe(2L, "6e B")));

            DashboardResponse response = service.stats(EducationCycle.COLLEGE);

            assertThat(response.getTotalStudents()).isEqualTo(42L);
            assertThat(response.getActiveStudents()).isEqualTo(30L);
            assertThat(response.getTotalClasses()).isEqualTo(2L);
            verify(studentRepository, never()).count();
            verify(classRepository, never()).findAll();
        }

        @Test
        @DisplayName("le cycle est transmis aux présences et à la répartition par genre")
        void cycleTransmisAuxAgregats() {
            when(attendanceRepository.countByDateAndStatusAndCycle(
                    today, AttendanceStatus.PRESENT, EducationCycle.LYCEE)).thenReturn(11L);
            when(attendanceRepository.countByDateAndStatusAndCycle(
                    today, AttendanceStatus.ABSENT, EducationCycle.LYCEE)).thenReturn(2L);
            when(attendanceRepository.countByDateAndStatusAndCycle(
                    today, AttendanceStatus.LATE, EducationCycle.LYCEE)).thenReturn(3L);
            when(studentRepository.countByGender(EducationCycle.LYCEE))
                    .thenReturn(List.<Object[]>of(new Object[]{"MALE", 5L}));

            DashboardResponse response = service.stats(EducationCycle.LYCEE);

            assertThat(response.getTodayPresent()).isEqualTo(11L);
            assertThat(response.getTodayAbsent()).isEqualTo(2L);
            assertThat(response.getTotalLate()).isEqualTo(3L);
            verify(studentRepository).countByGender(EducationCycle.LYCEE);
        }
    }

    // =====================================================================
    // Présences
    // =====================================================================

    @Test
    @DisplayName("chaque statut de présence alimente son propre compteur")
    void presencesParStatut() {
        // Un PRESENT/ABSENT inversé ne casse rien : les deux compteurs restent
        // des nombres plausibles à l'écran.
        when(attendanceRepository.countByDateAndStatusAndCycle(today, AttendanceStatus.PRESENT, null))
                .thenReturn(11L);
        when(attendanceRepository.countByDateAndStatusAndCycle(today, AttendanceStatus.ABSENT, null))
                .thenReturn(2L);
        when(attendanceRepository.countByDateAndStatusAndCycle(today, AttendanceStatus.LATE, null))
                .thenReturn(3L);

        DashboardResponse response = service.stats();

        assertThat(response.getTodayPresent()).isEqualTo(11L);
        assertThat(response.getTodayAbsent()).isEqualTo(2L);
        assertThat(response.getTotalLate()).isEqualTo(3L);
    }

    // =====================================================================
    // Bornes de dates des agrégats financiers
    // =====================================================================

    @Nested
    @DisplayName("Bornes de dates des montants du mois")
    class BornesFinancieres {

        @Test
        @DisplayName("les recettes vont du 1er du mois à la fin de la journée")
        void bornesDesRecettes() {
            service.stats();

            verify(paymentRepository).sumBetween(
                    currentMonth.atDay(1).atStartOfDay(),
                    today.atTime(LocalTime.MAX));
        }

        @Test
        @DisplayName("les dépenses vont du 1er du mois à DEMAIN (borne exclusive)")
        void bornesDesDepenses() {
            // `sumBetween` des dépenses prend des LocalDate et une borne
            // exclusive : passer `today` au lieu de `today.plusDays(1)` exclut
            // silencieusement toutes les dépenses du jour.
            service.stats();

            verify(expenseRepository).sumBetween(currentMonth.atDay(1), today.plusDays(1));
        }
    }

    // =====================================================================
    // Graphiques
    // =====================================================================

    @Nested
    @DisplayName("Graphiques")
    class Graphiques {

        @Test
        @DisplayName("six mois de recettes, du plus ancien au plus récent")
        void sixMoisDansLOrdre() {
            // monthlyRevenue interroge la MÊME méthode avec d'autres bornes :
            // sans ce stub, les strict stubs lèvent PotentialStubbingProblem.
            when(paymentRepository.sumBetween(
                    currentMonth.atDay(1).atStartOfDay(), today.atTime(LocalTime.MAX)))
                    .thenReturn(BigDecimal.ZERO);
            for (int i = 5; i >= 0; i--) {
                YearMonth month = currentMonth.minusMonths(i);
                when(paymentRepository.sumBetween(
                        month.atDay(1).atStartOfDay(),
                        month.plusMonths(1).atDay(1).atStartOfDay()))
                        .thenReturn(BigDecimal.valueOf(i * 100L));
            }

            List<DashboardResponse.ChartPoint> points = service.stats().getRevenueByMonth();

            assertThat(points).hasSize(6);
            // i = 5 pour le plus ancien, i = 0 pour le mois courant.
            assertThat(points.get(0).getValue()).isEqualTo(BigDecimal.valueOf(500L));
            assertThat(points.get(5).getValue()).isEqualTo(BigDecimal.ZERO);
            assertThat(points.get(5).getLabel())
                    .isEqualTo(currentMonth.format(DateTimeFormatter.ofPattern("MMM")));
        }

        @Test
        @DisplayName("une entrée par classe, avec son effectif")
        void elevesParClasse() {
            when(classRepository.findAll()).thenReturn(List.of(
                    classe(1L, "6e A"), classe(2L, "6e B")));
            when(studentRepository.countBySchoolClassId(1L)).thenReturn(30L);
            when(studentRepository.countBySchoolClassId(2L)).thenReturn(28L);

            List<DashboardResponse.ChartPoint> points = service.stats().getStudentsByClass();

            assertThat(points).extracting(DashboardResponse.ChartPoint::getLabel)
                    .containsExactly("6e A", "6e B");
            assertThat(points).extracting(DashboardResponse.ChartPoint::getValue)
                    .containsExactly(30L, 28L);
        }

        @Test
        @DisplayName("répartition par genre : Garçons, Filles, Indéfini")
        void repartitionParGenre() {
            when(studentRepository.countByGender((EducationCycle) null)).thenReturn(List.of(
                    new Object[]{"MALE", 12L},
                    new Object[]{"FEMALE", 15L},
                    new Object[]{null, 1L}));

            List<DashboardResponse.ChartPoint> points = service.stats().getStudentsByGender();

            assertThat(points).extracting(DashboardResponse.ChartPoint::getLabel)
                    .containsExactly("Garçons", "Filles", "Indéfini");
            assertThat(points).extracting(DashboardResponse.ChartPoint::getValue)
                    .containsExactly(12L, 15L, 1L);
        }
    }

    // =====================================================================
    // Compteurs simples
    // =====================================================================

    @Test
    @DisplayName("enseignants, matières, livres et congés en attente")
    void compteursSimples() {
        when(teacherRepository.count()).thenReturn(9L);
        when(subjectRepository.count()).thenReturn(14L);
        when(bookRepository.count()).thenReturn(120L);
        when(leaveRepository.findByStatus(LeaveStatus.PENDING))
                .thenReturn(List.of(mock(com.school.entity.Leave.class), mock(com.school.entity.Leave.class)));

        DashboardResponse response = service.stats();

        assertThat(response.getTotalTeachers()).isEqualTo(9L);
        assertThat(response.getTotalSubjects()).isEqualTo(14L);
        assertThat(response.getTotalBooks()).isEqualTo(120L);
        assertThat(response.getPendingLeaves()).isEqualTo(2L);
    }

    // =====================================================================
    // Journal d'audit
    // =====================================================================

    @Nested
    @DisplayName("Journal d'audit")
    class Journal {

        @Test
        @DisplayName("un filtre vide ou blanc devient null, pas une chaîne vide")
        void filtresBlancsDeviennentNull() {
            // `''` passé à `LIKE '%%'` ne casse rien ici, mais `'   '` filtrerait
            // sur trois espaces et ne renverrait jamais rien.
            when(auditLogRepository.search(isNull(), isNull(), isNull(), isNull(), isNull(), any()))
                    .thenReturn(Page.empty());

            service.auditLogs("   ", "", null, null, null, 0, 20);

            verify(auditLogRepository).search(null, null, null, null, null, PageRequest.of(0, 20));
        }

        @Test
        @DisplayName("les filtres renseignés sont élagués")
        void filtresElagues() {
            when(auditLogRepository.search(any(), any(), any(), any(), any(), any()))
                    .thenReturn(Page.empty());

            service.auditLogs("  harouna  ", " CREATE ", " Book ", null, null, 0, 20);

            verify(auditLogRepository).search("harouna", "CREATE", "Book", null, null,
                    PageRequest.of(0, 20));
        }

        @Test
        @DisplayName("la borne haute couvre toute la journée de fin")
        void borneHauteCouvreLaJournee() {
            // `to.atStartOfDay()` exclurait le dernier jour de la période : une
            // recherche « du 1er au 24 » ne montrerait rien du 24.
            when(auditLogRepository.search(any(), any(), any(), any(), any(), any()))
                    .thenReturn(Page.empty());

            service.auditLogs(null, null, null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 24), 0, 20);

            verify(auditLogRepository).search(null, null, null,
                    LocalDate.of(2026, 10, 1).atStartOfDay(),
                    LocalDate.of(2026, 10, 24).atTime(LocalTime.MAX),
                    PageRequest.of(0, 20));
        }

        @Test
        @DisplayName("renvoie la page normalisée")
        void pageNormalisee() {
            when(auditLogRepository.search(any(), any(), any(), any(), any(), any()))
                    .thenReturn(Page.empty(PageRequest.of(2, 20)));

            PageResponse<?> response = service.auditLogs(null, null, null, null, null, 2, 20);

            assertThat(response.getPage()).isEqualTo(2);
            assertThat(response.getSize()).isEqualTo(20);
            assertThat(response.getContent()).isEmpty();
        }
    }
}
