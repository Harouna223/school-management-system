package com.school.integration;

import com.school.entity.Book;
import com.school.entity.Borrowing;
import com.school.entity.FeeType;
import com.school.entity.Invoice;
import com.school.entity.Leave;
import com.school.entity.RefreshToken;
import com.school.entity.Student;
import com.school.entity.Teacher;
import com.school.entity.User;
import com.school.enums.BorrowingStatus;
import com.school.enums.ContractType;
import com.school.enums.Gender;
import com.school.enums.InvoiceStatus;
import com.school.enums.LeaveStatus;
import com.school.enums.LeaveType;
import com.school.enums.StudentStatus;
import com.school.enums.TeacherStatus;
import com.school.repository.BookRepository;
import com.school.repository.BorrowingRepository;
import com.school.repository.FeeTypeRepository;
import com.school.repository.InvoiceRepository;
import com.school.repository.LeaveRepository;
import com.school.repository.RefreshTokenRepository;
import com.school.repository.StudentRepository;
import com.school.repository.TeacherRepository;
import com.school.repository.UserRepository;
import com.school.service.AuthService;
import com.school.service.FinanceService;
import com.school.service.HrService;
import com.school.service.LibraryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'integration des taches planifiees ({@code @Scheduled}).
 *
 * <p><b>Pourquoi ce fichier existe.</b> Les taches planifiees ne sont atteignables
 * que par le planificateur de Spring, qui les invoque <em>a travers le proxy</em>.
 * Un test unitaire (Mockito) appelle la meme methode sur un objet nu : il ne voit
 * donc ni le proxy, ni la transaction, ni le flush. C'est exactement pour cela que
 * la CI restait verte alors qu'une tache nocturne echouait en production.</p>
 *
 * <p><b>Le piege corrige ici.</b> {@code @Transactional} pose sur une methode
 * appelee par auto-invocation ({@code this.maMethode()}) est <em>inerte</em> :
 * l'appel ne passe pas par le proxy Spring, donc aucune transaction n'est ouverte.
 * Une requete {@code @Modifying} executee sans transaction leve alors
 * {@code TransactionRequiredException} — c'est le
 * {@code ERROR ... Executing an update/delete query} observe dans les logs du
 * conteneur. Les tests de ces taches doivent donc appeler le <b>point d'entree
 * {@code @Scheduled}</b>, jamais la methode interne.</p>
 *
 * <p>Chaque test verifie un invariant metier (« la tache fait-elle vraiment son
 * travail ? ») et relit la base en JDBC, hors du contexte de persistance, pour
 * qu'aucun cache de premier niveau ne puisse masquer un changement non flushe.</p>
 */
class ScheduledTasksIT extends AbstractIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private LibraryService libraryService;

    @Autowired
    private HrService hrService;

    @Autowired
    private FinanceService financeService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private BorrowingRepository borrowingRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private FeeTypeRepository feeTypeRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private LeaveRepository leaveRepository;

    @Autowired
    private UserRepository userRepository;

    private Connection connexion() throws Exception {
        return DriverManager.getConnection(MySqlTestContainer.JDBC_URL,
                MySqlTestContainer.USERNAME, MySqlTestContainer.PASSWORD);
    }

    // ------------------------------------------------------------------
    // 1. Purge des jetons de rafraichissement (AuthService, 3 h 30)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("la purge planifiee des jetons supprime REELLEMENT les lignes (transaction active)")
    void purgePlanifieeSupprimeVraimentLesJetons() throws Exception {
        User admin = userRepository.findByUsername("admin").orElseThrow();
        String revoque = "it-revoque-" + UUID.randomUUID();
        String expire = "it-expire-" + UUID.randomUUID();
        String valide = "it-valide-" + UUID.randomUUID();

        refreshTokenRepository.save(RefreshToken.builder()
                .user(admin).token(revoque).revoked(true)
                .expiryDate(LocalDateTime.now().plusDays(1)).build());
        refreshTokenRepository.save(RefreshToken.builder()
                .user(admin).token(expire).revoked(false)
                .expiryDate(LocalDateTime.now().minusDays(1)).build());
        refreshTokenRepository.save(RefreshToken.builder()
                .user(admin).token(valide).revoked(false)
                .expiryDate(LocalDateTime.now().plusDays(1)).build());

        // Point d'entree @Scheduled : le seul chemin qui traverse le proxy Spring.
        // Avant correction, cet appel levait TransactionRequiredException
        // (« Executing an update/delete query ») car deleteStale est @Modifying
        // et la purge etait appelee par auto-invocation.
        authService.scheduledPurgeStaleRefreshTokens();

        assertThat(jetonExiste(revoque)).as("jeton revoque supprime").isFalse();
        assertThat(jetonExiste(expire)).as("jeton expire supprime").isFalse();
        assertThat(jetonExiste(valide)).as("jeton valide conserve").isTrue();
    }

    private boolean jetonExiste(String token) throws Exception {
        try (Connection conn = connexion();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM refresh_tokens WHERE token = ?")) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    // ------------------------------------------------------------------
    // 2. Marquage des emprunts en retard (LibraryService, 4 h 00)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("le marquage planifie des emprunts bascule VRAIMENT le statut en base")
    void marquageBibliothequePlanifiePersisteLeStatut() throws Exception {
        Book book = bookRepository.save(Book.builder()
                .title("IT bibliotheque " + UUID.randomUUID())
                .quantity(1)
                .available(0)
                .build());
        Borrowing borrowing = borrowingRepository.save(Borrowing.builder()
                .book(book)
                .student(eleveDeTest())
                .borrowDate(LocalDate.now().minusDays(30))
                .dueDate(LocalDate.now().minusDays(1))
                .status(BorrowingStatus.BORROWED)
                .build());

        libraryService.scheduledMarkOverdue();

        // Sans transaction en ecriture, le changement d'etat d'une entite managee
        // n'est jamais flushe : la tache se termine sans erreur mais la base reste
        // inchangee, et la liste « en retard » reste vide pour toujours.
        assertThat(statutEmprunt(borrowing.getId()))
                .as("statut de l'emprunt en base apres la tache planifiee")
                .isEqualTo(BorrowingStatus.OVERDUE.name());
    }

    private String statutEmprunt(Long id) throws Exception {
        try (Connection conn = connexion();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT status FROM borrowings WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("emprunt present en base").isTrue();
                return rs.getString(1);
            }
        }
    }

    // ------------------------------------------------------------------
    // 3. Reconciliation des conges (HrService, 2 h 00)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("la reconciliation planifiee des conges bascule VRAIMENT l'enseignant en conge")
    void reconciliationCongesPlanifieePersisteLeStatut() throws Exception {
        Teacher teacher = teacherRepository.save(Teacher.builder()
                .employeeNo("ENS-IT-SCHED-" + UUID.randomUUID().toString().substring(0, 8))
                .firstName("Test")
                .lastName("Planifie")
                .gender(Gender.MALE)
                .hireDate(LocalDate.now().minusMonths(6))
                .contractType(ContractType.CDI)
                .salary(new BigDecimal("200000.00"))
                .status(TeacherStatus.ACTIVE)
                .build());

        leaveRepository.save(Leave.builder()
                .teacher(teacher)
                .type(LeaveType.ANNUAL)
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(1))
                .status(LeaveStatus.APPROVED)
                .build());

        hrService.scheduledReconcileTeacherLeaveStatus();

        assertThat(statutEnseignant(teacher.getId()))
                .as("statut de l'enseignant en base apres la tache planifiee")
                .isEqualTo(TeacherStatus.ON_LEAVE.name());
    }

    private String statutEnseignant(Long id) throws Exception {
        try (Connection conn = connexion();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT status FROM teachers WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("enseignant present en base").isTrue();
                return rs.getString(1);
            }
        }
    }

    // ------------------------------------------------------------------
    // 4. Passage des factures en retard (FinanceService, 1 h 30)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("le marquage planifie des factures bascule VRAIMENT le statut en base")
    void marquageFacturesPlanifiePersisteLeStatut() throws Exception {
        FeeType feeType = feeTypeRepository.findByName("IT-SCHED-FRAIS")
                .orElseGet(() -> feeTypeRepository.save(FeeType.builder()
                        .name("IT-SCHED-FRAIS")
                        .amount(new BigDecimal("10000.00"))
                        .build()));
        Invoice invoice = invoiceRepository.save(Invoice.builder()
                .invoiceNo("IT-SCHED-" + UUID.randomUUID().toString().substring(0, 8))
                .student(eleveDeTest())
                .feeType(feeType)
                .amount(new BigDecimal("10000.00"))
                .discount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO)
                .dueDate(LocalDate.now().minusDays(1))
                .status(InvoiceStatus.UNPAID)
                .build());

        // Controle positif : cette tache porte deja @Transactional sur son point
        // d'entree @Scheduled. Elle doit donc passer, et sert de reference pour
        // valider le correctif applique aux trois autres.
        financeService.scheduledMarkOverdue();

        assertThat(statutFacture(invoice.getId()))
                .as("statut de la facture en base apres la tache planifiee")
                .isEqualTo(InvoiceStatus.OVERDUE.name());
    }

    private String statutFacture(Long id) throws Exception {
        try (Connection conn = connexion();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT status FROM invoices WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("facture presente en base").isTrue();
                return rs.getString(1);
            }
        }
    }

    /** Eleve de test (matricule unique : les tests partagent la meme base). */
    private Student eleveDeTest() {
        return studentRepository.save(Student.builder()
                .matricule("IT-SCHED-" + UUID.randomUUID().toString().substring(0, 8))
                .firstName("Test")
                .lastName("Planifie")
                .gender(Gender.MALE)
                .enrollmentDate(LocalDate.now().minusMonths(1))
                .status(StudentStatus.ACTIVE)
                .build());
    }
}
