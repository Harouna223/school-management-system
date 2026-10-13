package com.school.service;

import com.school.entity.NumberSequence;
import com.school.repository.NumberSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Attribution des numéros séquentiels (factures, reçus élèves, reçus de paie).
 *
 * <p>Remplace les {@code count() + 1} disséminés dans les services : après une
 * suppression ou une restauration, un comptage Java pouvait réattribuer un
 * numéro déjà émis, ce que la contrainte unique rejetait en 409 (paiement
 * refusé, aucune reprise possible côté paie enseignant).</p>
 *
 * <p>L'incrément se fait sous verrou de ligne, dans une transaction indépendante
 * ({@link Propagation#REQUIRES_NEW}) : le numéro reste attribué même si l'appelant
 * échoue ensuite, et deux appels concurrents ne peuvent pas obtenir la même
 * valeur.</p>
 */
@Service
@RequiredArgsConstructor
public class NumberSequenceService {

    /** Compteur des numéros de facture (FAC-). */
    public static final String INVOICE = "FAC-";
    /** Compteur des reçus de paiement élève (RCP-). */
    public static final String PAYMENT_RECEIPT = "RCP-";
    /** Compteur des reçus de paie enseignant (REC-ENS-). */
    public static final String TEACHER_RECEIPT = "REC-ENS-";

    private final NumberSequenceRepository sequenceRepository;

    /**
     * Réserve et renvoie la valeur suivante d'un compteur.
     *
     * @param prefix préfixe du compteur (créé à la volée s'il n'existe pas encore)
     * @return valeur strictement croissante, jamais réutilisée
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long next(String prefix) {
        NumberSequence sequence = sequenceRepository.findWithLockingByPrefix(prefix)
                .orElseGet(() -> sequenceRepository.save(NumberSequence.builder()
                        .prefix(prefix)
                        .lastValue(0L)
                        .build()));
        long next = sequence.getLastValue() + 1;
        sequence.setLastValue(next);
        sequenceRepository.save(sequence);
        return next;
    }
}
