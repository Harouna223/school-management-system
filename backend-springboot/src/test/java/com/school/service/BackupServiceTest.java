package com.school.service;

import com.school.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de la RESTAURATION de sauvegarde.
 *
 * Pourquoi ce fichier existe
 * --------------------------
 * `restoreBackup` execute du SQL lu dans un fichier fourni par l'utilisateur.
 * Il applique pour cela une LISTE BLANCHE d'instructions (formes produites par
 * `exportBackup`) : `DROP DATABASE`, `GRANT`, `CREATE USER`, `UPDATE`,
 * `DELETE`, `TRUNCATE`, ou du SQL ajoute apres la partie de donnees doivent
 * etre refuses. Aucun test ne couvrait ce code : une regression de la liste
 * blanche aurait transforme l'import d'une sauvegarde piegee en execution de
 * SQL arbitraire, sans qu'aucun test ne s'en apercoive.
 *
 * Tests unitaires purs (Mockito), sans contexte Spring : la suite backend ne
 * requiert donc toujours aucun MySQL.
 */
@ExtendWith(MockitoExtension.class)
class BackupServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private BackupService service;

    @BeforeEach
    void setUp() {
        service = new BackupService(jdbcTemplate);
    }

    /** Construit une sauvegarde bien formee : meme en-tete que `exportBackup()`. */
    private MockMultipartFile backup(String body) {
        String content = BackupService.BACKUP_MARKER + "\n" + body;
        return new MockMultipartFile("file", "sauvegarde.sql", "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
    }

    /** Instructions effectivement transmises a la base. */
    private List<String> executedStatements() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).execute(captor.capture());
        return captor.getAllValues();
    }

    /**
     * Instructions executees, sans le « ; » final.
     * `restoreBackup` transmet l'instruction telle quelle (point-virgule compris) ;
     * on le retire ici pour comparer a la forme lisible.
     */
    private List<String> executedStatementsSansPointVirgule() {
        return executedStatements().stream()
                .map(s -> s.endsWith(";") ? s.substring(0, s.length() - 1).trim() : s)
                .toList();
    }

    // ------------------------------------------------------------------
    // Cas nominal
    // ------------------------------------------------------------------

    @Test
    @DisplayName("restaure une sauvegarde valide et renvoie le nombre d'instructions executees")
    void restaureUneSauvegardeValide() {
        int executed = service.restoreBackup(backup("""
                SET FOREIGN_KEY_CHECKS = 0;
                DROP TABLE IF EXISTS `users`;
                CREATE TABLE `users` (
                  `id` bigint NOT NULL,
                  `username` varchar(255) NOT NULL
                );
                INSERT INTO `users` VALUES (1, 'admin');
                """));

        assertThat(executed).isEqualTo(4);
        assertThat(executedStatementsSansPointVirgule())
                .contains("DROP TABLE IF EXISTS `users`")
                .contains("INSERT INTO `users` VALUES (1, 'admin')");
    }

    @Test
    @DisplayName("accepte une definition de table dont un COMMENT contient une parenthese")
    void accepteUneParentheseDansUnCommentaire() {
        // Regression : l'ancien code cherchait la DERNIERE parenthese du texte, ce
        // qui tronquait la definition des qu'un COMMENT en contenait une.
        int executed = service.restoreBackup(backup("""
                CREATE TABLE `fees` (
                  `amount` decimal(10,2) NOT NULL COMMENT 'Montant (TTC)',
                  `label` varchar(50) NOT NULL
                );
                """));

        assertThat(executed).isEqualTo(1);
    }

    @Test
    @DisplayName("ne coupe pas une instruction sur un point-virgule situe dans un litteral")
    void neCoupePasSurUnPointVirguleDansUnLitteral() {
        int executed = service.restoreBackup(backup(
                "INSERT INTO `users` VALUES (1, 'a;b');\n"
                        + "INSERT INTO `users` VALUES (2, 'c');\n"));

        // Deux instructions, et non trois : le « ; » interne reste dans la valeur.
        assertThat(executed).isEqualTo(2);
        assertThat(executedStatementsSansPointVirgule()).contains("INSERT INTO `users` VALUES (1, 'a;b')");
    }

    @Test
    @DisplayName("ignore les commentaires et les lignes vides")
    void ignoreLesCommentairesEtLignesVides() {
        int executed = service.restoreBackup(backup("""
                -- commentaire de tete

                INSERT INTO `users` VALUES (1);

                -- commentaire de queue
                """));

        assertThat(executed).isEqualTo(1);
    }

    @Test
    @DisplayName("reactive les cles etrangeres a la fin")
    void reactiveLesClesEtrangeres() {
        service.restoreBackup(backup("INSERT INTO `users` VALUES (1);"));

        assertThat(executedStatements()).contains("SET FOREIGN_KEY_CHECKS = 1");
    }

    @Test
    @DisplayName("un dump produit par exportBackup est accepte par restoreBackup (aller-retour)")
    void allerRetourExportPuisRestauration() {
        // Le test le plus important de ce fichier : il verifie que les DEUX moities
        // du service s'accordent. C'est exactement ce que la regression
        // « if (!current.isEmpty()) throw » avait casse — le dump genere par
        // l'application etait refuse par sa propre restauration, alors que le code
        // compilait et que la CI etait verte.
        doReturn(List.of("users")).when(jdbcTemplate)
                .query(contains("information_schema"), any(RowMapper.class));
        doReturn(List.of("CREATE TABLE `users` ("
                + "`id` bigint NOT NULL, "
                + "`username` varchar(255) DEFAULT NULL COMMENT 'Nom (usuel)'"
                + ") ENGINE=InnoDB")).when(jdbcTemplate)
                .query(eq("SHOW CREATE TABLE `users`"), any(RowMapper.class));
        doReturn(List.of(List.of(1L, "admin"))).when(jdbcTemplate)
                .query(eq("SELECT * FROM `users`"), any(RowMapper.class));

        byte[] dump = service.exportBackup();
        String dumpText = new String(dump, StandardCharsets.UTF_8);

        // Le dump porte bien l'en-tete attendu par la restauration.
        assertThat(dumpText).startsWith(BackupService.BACKUP_MARKER);

        int executed = service.restoreBackup(
                new MockMultipartFile("file", "sauvegarde.sql", "text/plain", dump));

        // SET FOREIGN_KEY_CHECKS = 0, DROP, CREATE, INSERT, SET … = 1
        assertThat(executed).isEqualTo(5);
        assertThat(executedStatementsSansPointVirgule())
                .contains("DROP TABLE IF EXISTS `users`")
                .contains("INSERT INTO `users` VALUES (1, 'admin')")
                .contains("SET FOREIGN_KEY_CHECKS = 1");
    }

    // ------------------------------------------------------------------
    // Liste blanche : instructions refusees
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "refuse : {0}")
    @ValueSource(strings = {
            "DROP DATABASE school",
            "DROP DATABASE IF EXISTS school",
            "DROP TABLE `users`",
            "TRUNCATE TABLE `users`",
            "GRANT ALL PRIVILEGES ON *.* TO 'x'@'%'",
            "CREATE USER 'x'@'%' IDENTIFIED BY 'y'",
            "UPDATE `users` SET `username` = 'pirate'",
            "DELETE FROM `users`",
            "SET GLOBAL general_log = 'ON'",
            "LOAD DATA INFILE '/tmp/x' INTO TABLE `users`",
            // SQL ajoute APRES la partie de donnees (exfiltration de fichier)
            "INSERT INTO `users` VALUES (1) SELECT 1 INTO OUTFILE '/tmp/x'",
            "CREATE TABLE `users` (`id` int) SELECT 1 INTO OUTFILE '/tmp/x'",
    })
    void refuseTouteInstructionHorsListeBlanche(String dangerous) {
        assertThatThrownBy(() -> service.restoreBackup(backup(dangerous + ";")))
                .isInstanceOf(BusinessException.class)
                // « refusée » : on s'arrete avant l'accent pour rester robuste.
                .hasMessageContaining("Instruction refus");

        // Rien de dangereux n'atteint la base : seul le finally (rearmement des
        // cles etrangeres) doit avoir ete execute.
        assertThat(executedStatements()).containsOnly("SET FOREIGN_KEY_CHECKS = 1");
    }

    @Test
    @DisplayName("reactive les cles etrangeres meme si une instruction est refusee")
    void reactiveLesClesEtrangeresApresUnRefus() {
        // Le finally doit s'executer : ne jamais laisser FOREIGN_KEY_CHECKS = 0
        // sur une connexion du pool apres un echec.
        assertThatThrownBy(() -> service.restoreBackup(backup("""
                SET FOREIGN_KEY_CHECKS = 0;
                DROP DATABASE school;
                """)))
                .isInstanceOf(BusinessException.class);

        assertThat(executedStatements()).contains("SET FOREIGN_KEY_CHECKS = 1");
    }

    // ------------------------------------------------------------------
    // Fichier invalide
    // ------------------------------------------------------------------

    @Test
    @DisplayName("refuse un fichier sans l'en-tete de sauvegarde")
    void refuseUnFichierSansEnTete() {
        MockMultipartFile notABackup = new MockMultipartFile("file", "x.sql", "text/plain",
                "DROP TABLE `users`;\n".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.restoreBackup(notABackup))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("non reconnu");
    }

    @Test
    @DisplayName("refuse un fichier absent ou vide")
    void refuseUnFichierAbsentOuVide() {
        assertThatThrownBy(() -> service.restoreBackup(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Aucun fichier");

        assertThatThrownBy(() -> service.restoreBackup(
                new MockMultipartFile("file", "vide.sql", "text/plain", new byte[0])))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Aucun fichier");
    }

    @Test
    @DisplayName("refuse une derniere instruction non terminee par un point-virgule")
    void refuseUneInstructionNonTerminee() {
        assertThatThrownBy(() -> service.restoreBackup(backup("INSERT INTO `users` VALUES (1)")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("corrompue");
    }

    @Test
    @DisplayName("refuse un fichier au-dela de 100 Mo sans le lire")
    void refuseUnFichierTropVolumineux() throws Exception {
        MultipartFile tooBig = mock(MultipartFile.class);
        when(tooBig.isEmpty()).thenReturn(false);
        when(tooBig.getSize()).thenReturn(101L * 1024 * 1024);

        assertThatThrownBy(() -> service.restoreBackup(tooBig))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("volumineux");

        // Le contenu n'est meme pas lu : le refus se fait sur la taille.
        verify(tooBig, never()).getInputStream();
    }
}
