package com.school.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Formulaire « Informations de l'établissement ».
 *
 * <p>Seul le nom officiel est obligatoire : tous les autres champs peuvent
 * rester vides sans bloquer l'enregistrement. Les téléphones acceptent un
 * format local ou international (espaces, tirets, parenthèses, préfixe +).</p>
 */
@Data
public class SchoolInfoRequest {

    // A. Identité
    @NotBlank(message = "Le nom officiel de l'établissement est obligatoire")
    @Size(max = 150, message = "Le nom ne peut dépasser 150 caractères")
    private String name;

    @Size(max = 50, message = "Le sigle ne peut dépasser 50 caractères")
    private String shortName;

    @Size(max = 60)
    private String type;

    @Size(max = 60)
    private String status;

    @Size(max = 60, message = "Le numéro d'identification ne peut dépasser 60 caractères")
    private String identificationNumber;

    @Size(max = 200, message = "La devise ne peut dépasser 200 caractères")
    private String motto;

    @Min(value = 1800, message = "L'année de création semble invalide")
    @Max(value = 2200, message = "L'année de création semble invalide")
    private Integer creationYear;

    // B. Coordonnées
    @Size(max = 255)
    private String address;

    @Size(max = 120)
    private String district;

    @Size(max = 120)
    private String city;

    @Size(max = 120)
    private String region;

    @Size(max = 80)
    private String country;

    @Pattern(regexp = "^$|^[0-9+().\\s-]{6,30}$",
            message = "Le téléphone principal contient des caractères invalides")
    private String phone;

    @Pattern(regexp = "^$|^[0-9+().\\s-]{6,30}$",
            message = "Le téléphone secondaire contient des caractères invalides")
    private String phoneAlt;

    @Email(message = "L'adresse e-mail de l'établissement est invalide")
    @Size(max = 150)
    private String email;

    @Size(max = 150, message = "L'adresse du site web ne peut dépasser 150 caractères")
    private String website;

    // C. Administration
    @Size(max = 150)
    private String directorName;

    @Size(max = 100)
    private String directorRole;

    @Pattern(regexp = "^$|^[0-9+().\\s-]{6,30}$",
            message = "Le téléphone du responsable contient des caractères invalides")
    private String directorPhone;

    @Email(message = "L'adresse e-mail du responsable est invalide")
    @Size(max = 150)
    private String directorEmail;

    @Size(max = 80)
    private String accreditationNumber;

    @Size(max = 1000, message = "Les informations complémentaires ne peuvent dépasser 1000 caractères")
    private String additionalInfo;
}
