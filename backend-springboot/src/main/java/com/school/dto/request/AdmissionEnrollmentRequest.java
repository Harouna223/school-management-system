package com.school.dto.request;

import com.school.enums.EducationCycle;
import com.school.enums.Gender;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Inscription d'une candidature acceptée : informations civiles et de
 * scolarité qui ne figurent pas dans le dossier d'admission.
 *
 * <p>Le prénom, le nom, la date de naissance et les coordonnées viennent de la
 * candidature. Seul le genre est indispensable : l'entité {@code Student}
 * l'exige et le dossier d'admission ne le collecte pas.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdmissionEnrollmentRequest {

    @NotNull(message = "Le genre est obligatoire")
    private Gender gender;

    /** Classe d'accueil : facultative (un étudiant universitaire n'en a pas). */
    private Long classId;

    /** Cycle d'enseignement ; déduit de la classe si non précisé. */
    private EducationCycle educationCycle;

    /** Par défaut, la date du jour. */
    private LocalDate enrollmentDate;
}
