package com.school.utils;

import com.school.exception.BusinessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Pagination bornée.
 *
 * <p>Les contrôleurs transmettaient {@code page} et {@code size} bruts aux
 * services, qui appelaient directement {@code PageRequest.of} : un client
 * pouvait demander {@code size=1000000} et faire charger toute une table en
 * mémoire (le front demandait déjà {@code size=10000}). Toute pagination
 * exposée passe désormais par ici.</p>
 *
 * <p>Une valeur hors bornes est refusée (400) plutôt que silencieusement
 * ramenée : le client saurait sinon recevoir une page tronquée sans le
 * comprendre.</p>
 */
public final class Pagination {

    /** Taille par défaut quand le client n'en fournit pas. */
    public static final int DEFAULT_SIZE = 20;

    /** Taille maximale acceptée : au-delà, l'écran doit paginer ou filtrer. */
    public static final int MAX_SIZE = 200;

    private Pagination() {
    }

    public static Pageable of(int page, int size) {
        return of(page, size, Sort.unsorted());
    }

    public static Pageable of(int page, int size, Sort sort) {
        if (page < 0) {
            throw new BusinessException("Paramètre invalide : page doit être supérieure ou égale à 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessException(
                    "Paramètre invalide : size doit être compris entre 1 et " + MAX_SIZE);
        }
        return PageRequest.of(page, size, sort != null ? sort : Sort.unsorted());
    }
}
