package com.school.exception;

/**
 * Levée lorsqu'une ressource demandée n'existe pas.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String entity, Long id) {
        return new ResourceNotFoundException(entity + " introuvable avec l'identifiant : " + id);
    }
}
