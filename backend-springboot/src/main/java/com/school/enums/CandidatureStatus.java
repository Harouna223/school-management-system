package com.school.enums;

/**
 * Statut d'une candidature d'admission universitaire.
 */
public enum CandidatureStatus {
    EN_ATTENTE,
    RECUE,
    ACCEPTEE,
    LISTE_ATTENTE,
    REFUSEE,
    /** L'élève a été créé : la candidature n'est plus un simple dossier. */
    INSCRITE
}