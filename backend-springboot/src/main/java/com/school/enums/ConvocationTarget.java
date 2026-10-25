package com.school.enums;

/**
 * Destinataire d'une convocation : un élève, un parent d'élève ou un enseignant.
 *
 * <p>La cible détermine qui est notifié et quel rattachement est obligatoire
 * ({@code studentId}, {@code parentId} ou {@code teacherId}).</p>
 */
public enum ConvocationTarget {

    /** Convocation d'un élève (le parent est prévenu en tant que tuteur). */
    ELEVE,

    /** Convocation du parent lui-même (entretien, impayés, comportement…). */
    PARENT,

    /** Convocation d'un enseignant (conseil, remise de notes, discipline…). */
    ENSEIGNANT
}
