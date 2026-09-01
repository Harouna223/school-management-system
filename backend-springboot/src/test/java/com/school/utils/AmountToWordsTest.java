package com.school.utils;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmountToWordsTest {

    @Test
    void zero() {
        assertThat(AmountToWords.toWords(BigDecimal.ZERO)).isEqualTo("zéro francs CFA");
    }

    @Test
    void simpleAmounts() {
        assertThat(AmountToWords.toWords(new BigDecimal("1"))).isEqualTo("un francs CFA");
        assertThat(AmountToWords.toWords(new BigDecimal("21"))).isEqualTo("vingt et un francs CFA");
        assertThat(AmountToWords.toWords(new BigDecimal("80"))).isEqualTo("quatre-vingts francs CFA");
        assertThat(AmountToWords.toWords(new BigDecimal("91"))).isEqualTo("quatre-vingt-onze francs CFA");
        assertThat(AmountToWords.toWords(new BigDecimal("71"))).isEqualTo("soixante et onze francs CFA");
        assertThat(AmountToWords.toWords(new BigDecimal("70"))).isEqualTo("soixante-dix francs CFA");
    }

    @Test
    void hundreds() {
        assertThat(AmountToWords.toWords(new BigDecimal("100"))).isEqualTo("cent francs CFA");
        assertThat(AmountToWords.toWords(new BigDecimal("200"))).isEqualTo("deux cents francs CFA");
        assertThat(AmountToWords.toWords(new BigDecimal("250"))).isEqualTo("deux cent cinquante francs CFA");
    }

    @Test
    void thousandsAndMillions() {
        assertThat(AmountToWords.toWords(new BigDecimal("1000"))).isEqualTo("mille francs CFA");
        assertThat(AmountToWords.toWords(new BigDecimal("12500")))
                .isEqualTo("douze mille cinq cents francs CFA");
        assertThat(AmountToWords.toWords(new BigDecimal("1000000")))
                .isEqualTo("un million francs CFA");
        assertThat(AmountToWords.toWords(new BigDecimal("2000000")))
                .isEqualTo("deux millions francs CFA");
    }

    @Test
    void withCents() {
        assertThat(AmountToWords.toWords(new BigDecimal("12500.50")))
                .isEqualTo("douze mille cinq cents francs CFA et 50 centimes");
        assertThat(AmountToWords.toWords(new BigDecimal("10.05")))
                .isEqualTo("dix francs CFA et 05 centimes");
    }

    @Test
    void tooLarge() {
        assertThatThrownBy(() -> AmountToWords.toWords(new BigDecimal("1000000000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullAmount() {
        assertThat(AmountToWords.toWords(null)).isEmpty();
    }
}