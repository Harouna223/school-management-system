package com.school.utils;

import com.school.exception.BusinessException;

/**
 * Politique de mot de passe commune : 8 caractères minimum, lettres et chiffres.
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.length() < 8) {
            throw new BusinessException("Le mot de passe doit contenir au moins 8 caractères");
        }
        if (!password.matches(".*[a-z].*")
                || !password.matches(".*[A-Z].*")
                || !password.matches(".*[0-9].*")) {
            throw new BusinessException(
                    "Le mot de passe doit contenir au moins une minuscule, une majuscule et un chiffre");
        }
    }
}