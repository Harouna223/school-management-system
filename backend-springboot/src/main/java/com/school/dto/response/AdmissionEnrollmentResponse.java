package com.school.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Résultat de l'inscription d'une candidature : le dossier mis à jour et
 * l'élève créé.
 *
 * <p>{@code student} est un {@link StudentResponse} complet : il porte le
 * matricule attribué et, le cas échéant, le mot de passe du compte élève généré,
 * communiqué une seule fois.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdmissionEnrollmentResponse {

    private CandidatureResponse candidature;
    private StudentResponse student;
}
