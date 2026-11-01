package com.school.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UsernameGenerator} : garantir un nom libre sans jamais renvoyer un nom
 * déjà attribué.
 *
 * <p>L'enjeu n'est pas cosmétique : {@code createLinkedAccount} réutilise un
 * compte qui porte déjà le nom demandé. Renvoyer un nom occupé rattache donc un
 * dossier (élève, parent, enseignant) au compte d'une autre personne.</p>
 */
class UsernameGeneratorTest {

    @Test
    @DisplayName("le nom demandé est rendu tel quel quand il est libre")
    void nomLibreRenduTelQuel() {
        assertThat(UsernameGenerator.unique("aicha.kamdem", "eleve", n -> false))
                .isEqualTo("aicha.kamdem");
    }

    @Test
    @DisplayName("un nom pris reçoit un suffixe numérique croissant")
    void nomPrisRecoitUnSuffixe() {
        Set<String> pris = Set.of("aicha.kamdem", "aicha.kamdem2");

        assertThat(UsernameGenerator.unique("aicha.kamdem", "eleve", pris::contains))
                .isEqualTo("aicha.kamdem3");
    }

    @Test
    @DisplayName("une base vide ou nulle retombe sur la racine de repli")
    void baseVideUtiliseLeRepli() {
        assertThat(UsernameGenerator.unique(null, "parent", n -> false)).isEqualTo("parent");
        assertThat(UsernameGenerator.unique("   ", "enseignant", n -> false)).isEqualTo("enseignant");
    }

    @Test
    @DisplayName("la base est débarrassée de ses espaces avant usage")
    void baseEstNettoyee() {
        assertThat(UsernameGenerator.unique("  jean.dupont  ", "eleve", n -> false))
                .isEqualTo("jean.dupont");
    }

    @Test
    @DisplayName("le résultat tient dans les 50 caractères de users.username")
    void resultatBorneALaColonne() {
        String base = "a".repeat(50);

        String genere = UsernameGenerator.unique(base, "eleve", Set.of(base)::contains);

        // Sans troncature, « racine + 2 » faisait 51 caractères : l'insertion
        // échouait en base au lieu de produire un nom valide.
        assertThat(genere).hasSize(50).endsWith("2");
    }
}
