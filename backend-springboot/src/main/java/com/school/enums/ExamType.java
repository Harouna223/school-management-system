package com.school.enums;

/**
 * Types d'évaluation : contrôle, devoir, examen, baccalauréat.
 */
public enum ExamType {
    CONTROLE,
    DEVOIR,
    EXAMEN,
    BACCALAUREAT,

    /**
     * Composition : épreuve de synthèse du trimestre. C'est la SEULE valeur
     * qui alimente la colonne « Note/compo » du bulletin ; toutes les autres
     * alimentent « Note/classe ».
     */
    COMPOSITION
}
