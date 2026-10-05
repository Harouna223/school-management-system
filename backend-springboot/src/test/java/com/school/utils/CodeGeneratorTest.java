package com.school.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests du générateur de mots de passe aléatoires.
 *
 * <p>Ce générateur remplace les mots de passe par défaut connus (« Eleve@123 »,
 * « Enseignant@123 », « Parent@123 ») : si sa sortie sortait de la politique de
 * sécurité, la création de comptes échouerait en production — d'où le contrôle
 * systématique sur 200 tirages.</p>
 */
class CodeGeneratorTest {

    @Test
    @DisplayName("randomPassword respecte TOUJOURS la politique (≥8 car., minuscule, majuscule, chiffre)")
    void randomPasswordRespecteLaPolitique() {
        for (int i = 0; i < 200; i++) {
            String password = CodeGenerator.randomPassword();

            assertThat(password).hasSizeGreaterThanOrEqualTo(8);
            // La politique est la source de vérité : aucune divergence possible.
            assertThatCode(() -> PasswordPolicy.validate(password)).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("randomPassword est réellement aléatoire (aucune collision sur 200 tirages)")
    void randomPasswordEstAleatoire() {
        Set<String> valeurs = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            valeurs.add(CodeGenerator.randomPassword());
        }

        assertThat(valeurs).hasSize(200);
    }

    @Test
    @DisplayName("les matricules et numéros conservent leur format")
    void formatsDeCodesStables() {
        assertThat(CodeGenerator.studentMatricule(1L)).matches("ETU-\\d{4}-000001");
        assertThat(CodeGenerator.teacherEmployeeNo(7L)).matches("ENS-\\d{4}-0007");
        assertThat(CodeGenerator.receiptNo(12L)).matches("RCP-\\d{6}-00012");
        assertThat(CodeGenerator.invoiceNo(3L)).matches("FAC-\\d{6}-00003");
    }
}