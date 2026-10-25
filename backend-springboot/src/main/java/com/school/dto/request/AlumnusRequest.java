package com.school.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ajout / modification d'un diplômé (alumni).
 *
 * <p>Remplace l'entité {@code Alumnus} en corps de requête.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlumnusRequest {

    /** Élève d'origine, facultatif (un diplômé peut être saisi manuellement). */
    private Long studentId;

    @NotBlank(message = "Le prénom est obligatoire")
    @Size(max = 100, message = "Le prénom ne peut pas dépasser 100 caractères")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 100, message = "Le nom ne peut pas dépasser 100 caractères")
    private String lastName;

    @Size(max = 150, message = "La filière ne peut pas dépasser 150 caractères")
    private String fieldName;

    @Size(max = 20, message = "Le diplôme ne peut pas dépasser 20 caractères")
    private String diploma;

    @Size(max = 20, message = "L'année ne peut pas dépasser 20 caractères")
    private String academicYear;

    @Email(message = "L'adresse e-mail est invalide")
    @Size(max = 150, message = "L'adresse e-mail ne peut pas dépasser 150 caractères")
    private String email;

    @Size(max = 30, message = "Le téléphone ne peut pas dépasser 30 caractères")
    private String phone;

    @Size(max = 200, message = "L'emploi actuel ne peut pas dépasser 200 caractères")
    private String currentJob;

    @Size(max = 200, message = "L'entreprise ne peut pas dépasser 200 caractères")
    private String company;

    @Size(max = 500, message = "Les notes ne peuvent pas dépasser 500 caractères")
    private String notes;
}
