package com.school.utils;

import java.math.BigDecimal;

/**
 * Échelle d'appréciation d'une moyenne sur 20.
 *
 * <p>Une seule définition, utilisée à la fois pour la colonne
 * « Observation » de chaque matière et pour l'appréciation générale du
 * bulletin. Les bornes hautes sont EXCLUSIVES (sauf 20) : 17,99 est
 * « Très bien », 18,00 est « Excellent ».</p>
 */
public final class GradeObservation {

    public static final String NONE = "—";

    private GradeObservation() {
    }

    /**
     * @param average moyenne sur 20 (peut être {@code null})
     * @return l'appréciation correspondante, ou {@link #NONE} si la moyenne
     *         est absente
     */
    public static String of(BigDecimal average) {
        if (average == null) {
            return NONE;
        }
        double value = average.doubleValue();
        if (value >= 18) {
            return "Excellent";
        }
        if (value >= 16) {
            return "Très bien";
        }
        if (value >= 14) {
            return "Bien";
        }
        if (value >= 12) {
            return "Assez bien";
        }
        if (value >= 10) {
            return "Passable";
        }
        if (value >= 8) {
            return "Insuffisant";
        }
        return "Très insuffisant";
    }
}
