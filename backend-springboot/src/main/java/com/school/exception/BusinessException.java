package com.school.exception;

/**
 * Levée lors d'une requête métier invalide (conflit, règle de gestion...).
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
