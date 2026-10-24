package com.school.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Informations officielles de l'établissement, regroupées par section.
 *
 * <p>Vue structurée des clés {@code SCHOOL_*} de la table {@code settings} :
 * une seule source de vérité, déjà utilisée par les en-têtes PDF
 * ({@link com.school.pdf.SchoolDocumentTheme}) et par l'écran Paramètres.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchoolInfoResponse {

    // A. Identité
    private String name;
    private String shortName;
    private String type;
    private String status;
    private String identificationNumber;
    private String motto;
    private Integer creationYear;

    // B. Coordonnées
    private String address;
    private String district;
    private String city;
    private String region;
    private String country;
    private String phone;
    private String phoneAlt;
    private String email;
    private String website;

    // C. Administration
    private String directorName;
    private String directorRole;
    private String directorPhone;
    private String directorEmail;
    private String accreditationNumber;
    private String additionalInfo;

    // Logo
    private String logoUrl;
}
