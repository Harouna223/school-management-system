package com.school.dto.request;

import com.school.enums.EducationCycle;
import com.school.enums.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Création / mise à jour d'un élève (avec parent optionnel).
 * <p>
 * {@code classId} est optionnel : un apprenant universitaire (cycle UNIVERSITE)
 * n'est pas rattaché à une classe scolaire.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentRequest {

    @NotBlank(message = "Le prénom est obligatoire")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    private String lastName;

    private LocalDate birthDate;

    private String birthPlace;

    @NotNull(message = "Le genre est obligatoire")
    private Gender gender;

    private String address;
    private String phone;

    @Email(message = "Email invalide")
    private String email;

    private LocalDate enrollmentDate;

    private Long classId;

    /** Cycle d'enseignement ; déduit de la classe si non précisé. */
    private EducationCycle educationCycle;

    private Long parentId;

    // --- Parent (créé automatiquement si absent) ---
    private String parentFirstName;
    private String parentLastName;
    private String parentPhone;
    private String parentEmail;
    private String parentProfession;

    // --- Compte utilisateur optionnel ---
    private Boolean createUserAccount;
    private String username;
    private String password;

    // --- Compte parent optionnel ---
    private Boolean createParentAccount;
    private String parentUsername;
    private String parentPassword;
}