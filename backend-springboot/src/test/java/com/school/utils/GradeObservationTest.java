package com.school.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de l'échelle d'appréciation du bulletin.
 *
 * <p>Les bornes sont EXCLUSIVES en haut : 17,99 est « Très bien », 18,00 est
 * « Excellent ». C'est exactement la règle demandée par l'établissement.</p>
 */
class GradeObservationTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "20.00,  Excellent",
            "18.00,  Excellent",
            "17.99,  Très bien",
            "16.00,  Très bien",
            "15.99,  Bien",
            "14.00,  Bien",
            "13.99,  Assez bien",
            "12.00,  Assez bien",
            "11.99,  Passable",
            "10.00,  Passable",
            "9.99,   Insuffisant",
            "8.00,   Insuffisant",
            "7.99,   Très insuffisant",
            "0.00,   Très insuffisant",
    })
    @DisplayName("Chaque borne de l'échelle renvoie l'appréciation attendue")
    void bornes(String moyenne, String attendu) {
        assertThat(GradeObservation.of(new BigDecimal(moyenne))).isEqualTo(attendu);
    }

    @Test
    @DisplayName("Une moyenne absente n'invente pas d'appréciation")
    void moyenneAbsente() {
        assertThat(GradeObservation.of(null)).isEqualTo("—");
    }
}
