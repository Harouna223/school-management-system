package com.school.exception;

/**
 * Levée lors d'un échec de rafraîchissement ou de validation de jeton.
 *
 * <p>Le message ne contient JAMAIS la valeur du jeton reçu : ces messages sont
 * renvoyés au client et journalisés, et un jeton en clair dans une réponse ou
 * un journal est un secret exposé (il suffisait auparavant de provoquer un
 * refus pour faire recopier son propre jeton dans les logs).</p>
 */
public class TokenRefreshException extends RuntimeException {

    public TokenRefreshException(String message) {
        super(message);
    }
}
