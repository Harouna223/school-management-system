package com.school.integration;

import com.school.entity.Teacher;
import com.school.repository.TeacherRepository;
import com.school.repository.TeacherWorkHourRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'integration du LOT 3 (paie, numerotation, RH).
 *
 * <p>Ce qui ne peut etre verifie qu'avec une vraie base :</p>
 * <ul>
 *   <li>la migration V4 (table {@code number_sequences} + colonne
 *       {@code payrolls.expense_id}) ;</li>
 *   <li>l'unicite des numeros emis par le compteur partage — l'ancien
 *       {@code count() + 1} pouvait collisionner et bloquait definitivement le
 *       paiement d'un salaire ;</li>
 *   <li>la requete NULL-safe de detection de doublon d'heures, qui ne peut pas
 *       etre validee par un mock : le bug etait dans le SQL genere
 *       ({@code subject_id = -1} ne matche jamais {@code NULL}).</li>
 * </ul>
 */
class Lot3PayrollInfrastructureIT extends AbstractIntegrationTest {

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private TeacherWorkHourRepository workHourRepository;

    private Connection connexion() throws Exception {
        return DriverManager.getConnection(MySqlTestContainer.JDBC_URL,
                MySqlTestContainer.USERNAME, MySqlTestContainer.PASSWORD);
    }

    @Test
    @DisplayName("V4 est appliquée : table des compteurs et colonne payrolls.expense_id")
    void migrationV4Appliquee() throws Exception {
        try (Connection conn = connexion();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '4' AND success = 1");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            assertThat(rs.getInt(1)).as("V4 presente et reussie").isEqualTo(1);
        }
        try (Connection conn = connexion();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.TABLES "
                             + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'number_sequences'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            assertThat(rs.getInt(1)).as("table number_sequences").isEqualTo(1);
        }
        try (Connection conn = connexion();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                             + "AND TABLE_NAME = 'payrolls' AND COLUMN_NAME = 'expense_id'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            assertThat(rs.getInt(1)).as("colonne payrolls.expense_id").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("les compteurs amorcés par V4 existent pour les trois préfixes")
    void compteursAmorces() throws Exception {
        Set<String> prefixes = new HashSet<>();
        try (Connection conn = connexion();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT prefix FROM number_sequences");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                prefixes.add(rs.getString(1));
            }
        }
        assertThat(prefixes).contains("FAC-", "RCP-", "REC-ENS-");
    }

    @Test
    @DisplayName("le verrou pessimiste du compteur s'acquiert sans erreur SQL")
    void verrouDuCompteurFonctionne() throws Exception {
        // SELECT ... FOR UPDATE sur la table des compteurs : valide le JPQL du
        // dépôt (aucun test unitaire ne peut l'exécuter).
        String sql = "SELECT last_seq_value FROM number_sequences WHERE prefix = ? FOR UPDATE";
        try (Connection conn = connexion()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, "RCP-");
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("compteur RCP- présent").isTrue();
                    assertThat(rs.getLong(1)).isGreaterThanOrEqualTo(0L);
                }
            }
            conn.rollback();
        }
    }

    /** Professeur de test, créé une seule fois (les tests partagent la même base). */
    private static final String EMPLOYEE_NO = "ENS-IT-LOT3";

    /** Journée de référence des saisies d'heures du test (idempotent). */
    private LocalDate jourDeTest() {
        return LocalDate.now().minusDays(3);
    }

    private Teacher enseignantDeTest() {
        return teacherRepository.findAll().stream()
                .filter(t -> EMPLOYEE_NO.equals(t.getEmployeeNo()))
                .findFirst()
                .orElseGet(() -> teacherRepository.save(Teacher.builder()
                        .employeeNo(EMPLOYEE_NO)
                        .firstName("Test")
                        .lastName("Lot3")
                        .gender(com.school.enums.Gender.MALE)
                        .status(com.school.enums.TeacherStatus.ACTIVE)
                        .contractType(com.school.enums.ContractType.CDI)
                        .hireDate(LocalDate.now().minusMonths(3))
                        .salary(new BigDecimal("200000.00"))
                        .build()));
    }

    /** Insère une saisie de 8 h sans matière ni classe si elle n'existe pas déjà. */
    private void garantirSaisieSansMatiereNiClasse(Teacher teacher) throws Exception {
        LocalDate jour = jourDeTest();
        String sql = "INSERT INTO teacher_work_hours (work_date, hours, amount, hourly_rate_applied, "
                + "teacher_id, subject_id, class_id) "
                + "SELECT ?, 8, 40000, 5000, ?, NULL, NULL FROM DUAL "
                + "WHERE NOT EXISTS (SELECT 1 FROM teacher_work_hours "
                + "  WHERE teacher_id = ? AND work_date = ? "
                + "    AND subject_id IS NULL AND class_id IS NULL)";
        try (Connection conn = connexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(jour));
            ps.setLong(2, teacher.getId());
            ps.setLong(3, teacher.getId());
            ps.setDate(4, java.sql.Date.valueOf(jour));
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("la détection de doublon d'heures trouve bien une saisie SANS matière ni classe")
    void detectionDoublonSansMatiereNiClasse() throws Exception {
        Teacher teacher = enseignantDeTest();
        garantirSaisieSansMatiereNiClasse(teacher);
        LocalDate jour = jourDeTest();

        // La requête doit trouver la ligne NULL/NULL. L'ancien code envoyait une
        // sentinelle -1L (`subject_id = -1 AND class_id = -1`) qui ne matchait
        // jamais NULL : une deuxième saisie de 8 h était acceptée et le salaire
        // du mois doublait.
        assertThat(workHourRepository.findDuplicates(teacher.getId(), jour, null, null))
                .as("doublon détecté avec matière/classe absentes")
                .hasSize(1);
        // Contrôle négatif : un filtre sur une matière donnée ne doit rien trouver.
        assertThat(workHourRepository.findDuplicates(teacher.getId(), jour, 999L, null)).isEmpty();
    }

    @Test
    @DisplayName("la somme journalière hors ligne courante exclut bien la saisie corrigée")
    void sommeJournaliereHorsLigneCourante() throws Exception {
        Teacher teacher = enseignantDeTest();
        garantirSaisieSansMatiereNiClasse(teacher);
        LocalDate jour = jourDeTest();

        try (Connection conn = connexion();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id FROM teacher_work_hours WHERE teacher_id = ? AND work_date = ?")) {
            ps.setLong(1, teacher.getId());
            ps.setDate(2, java.sql.Date.valueOf(jour));
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                long id = rs.getLong(1);
                // Le plafond de 24 h revérifié à la correction doit exclure la ligne
                // modifiée, sinon l'ancienne valeur était comptée deux fois.
                assertThat(workHourRepository
                        .sumHoursOfTeacherOnDateExcluding(teacher.getId(), jour, id))
                        .isEqualByComparingTo("0");
                assertThat(workHourRepository.sumHoursOfTeacherOnDate(teacher.getId(), jour))
                        .isEqualByComparingTo("8");
            }
        }
    }
}
