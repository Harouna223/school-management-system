package com.school.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Jeton de lecture d'un fichier pédagogique.
 *
 * <p>Un élément {@code <video>} ou {@code <iframe>} ne peut pas porter
 * l'en-tête {@code Authorization} : le jeton voyage donc en paramètre de
 * requête, avec une durée de vie courte et une portée limitée à UNE ressource
 * et UN usage ({@code stream}, {@code preview} ou {@code download}).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseAccessTokenResponse {

    private Long resourceId;
    /** Usage autorisé : stream, preview, download. */
    private String purpose;
    private String token;
    /** Durée de validité restante, en secondes. */
    private long expiresInSeconds;
}
