package com.school.utils;

import java.util.function.Predicate;

/**
 * Génération de noms d'utilisateur libres.
 *
 * <p>{@code users.username} est {@code UNIQUE} en base : proposer un nom déjà
 * attribué à l'insertion produit une violation d'intégrité, donc un 409 opaque
 * pour l'utilisateur. Cet utilitaire suffixe le nom demandé
 * ({@code jean.dupont}, {@code jean.dupont2}, {@code jean.dupont3}…) jusqu'à en
 * trouver un disponible.</p>
 *
 * <p>Le prédicat d'occupation est fourni par l'appelant : chaque module
 * interroge son propre repository. Sans cela, la boucle était recopiée dans
 * {@code StudentService} et {@code TeacherService}, et avait été oubliée dans
 * {@code linkParentAccount} — où un parent homonyme d'un compte existant était
 * rattaché à ce compte.</p>
 */
public final class UsernameGenerator {

    /** Taille de la colonne {@code users.username}. */
    private static final int MAX_LENGTH = 50;

    private UsernameGenerator() {
    }

    /**
     * @param base     nom souhaité (peut être {@code null} ou vide)
     * @param fallback racine utilisée si {@code base} est vide
     *                 ({@code "eleve"}, {@code "parent"}…)
     * @param taken    vrai si le nom est déjà attribué
     * @return un nom libre, d'au plus {@value #MAX_LENGTH} caractères
     */
    public static String unique(String base, String fallback, Predicate<String> taken) {
        String root = root(base, fallback);
        String candidate = root;
        int suffix = 2;
        while (taken.test(candidate)) {
            candidate = truncate(root, suffix) + suffix;
            suffix++;
        }
        return candidate;
    }

    private static String root(String base, String fallback) {
        String value = base == null ? "" : base.trim();
        if (!value.isEmpty()) {
            return value;
        }
        return fallback == null || fallback.isBlank() ? "utilisateur" : fallback;
    }

    /**
     * Réserve la place du suffixe : sans cette troncature, un nom de 49
     * caractères suivi de « 2 » dépassait la colonne et échouait en base.
     */
    private static String truncate(String root, int suffix) {
        int max = MAX_LENGTH - String.valueOf(suffix).length();
        return root.length() <= max ? root : root.substring(0, max);
    }
}
