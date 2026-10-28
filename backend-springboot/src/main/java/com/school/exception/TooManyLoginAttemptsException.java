package com.school.exception;

/**
 * Trop d'échecs de connexion depuis une même adresse IP.
 *
 * <p>Le verrouillage par compte protège un compte ciblé ; cette limite protège
 * l'établissement contre le balayage de nombreux comptes depuis une même
 * origine. La réponse est un 429 avec un en-tête {@code Retry-After}.</p>
 */
public class TooManyLoginAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyLoginAttemptsException(long retryAfterSeconds) {
        super("Trop de tentatives de connexion. Veuillez réessayer plus tard.");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
