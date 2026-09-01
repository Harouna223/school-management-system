package com.school.exception;

/**
 * Levée lors d'un échec d'authentification (JWT invalide, expiré, accès refusé).
 */
public class TokenRefreshException extends RuntimeException {

    public TokenRefreshException(String token, String message) {
        super(String.format("Échec pour le jeton [%s] : %s", token, message));
    }
}
