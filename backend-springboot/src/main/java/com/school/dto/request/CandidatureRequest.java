package com.school.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Dépôt d'une candidature d'admission.
 *
 * <p>Remplace l'entité {@code Candidature} en corps de requête : la référence
 * et le statut initial sont attribués par le serveur, jamais par le client.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidatureRequest {

    @NotBlank(message = "Le prénom est obligatoire")
    @Size(max = 100, message = "Le prénom ne peut pas dépasser 100 caractères")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 100, message = "Le nom ne peut pas dépasser 100 caractères")
    private String lastName;

    private LocalDate birthDate;

    @Email(message = "L'adresse e-mail est invalide")
    @Size(max = 150, message = "L'adresse e-mail ne peut pas dépasser 150 caractères")
    private String email;

    @Size(max = 30, message = "Le téléphone ne peut pas dépasser 30 caractères")
    private String phone;

    @NotBlank(message = "La filière est obligatoire")
    @Size(max = 150, message = "La filière ne peut pas dépasser 150 caractères")
    private String fieldName;

    /** Niveau visé, en texte libre (le cursus LMD n'est plus modélisé). */
    @Size(max = 20, message = "Le niveau ne peut pas dépasser 20 caractères")
    private String level;

    private String notes;
}
