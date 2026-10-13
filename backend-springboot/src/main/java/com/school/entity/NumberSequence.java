package com.school.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Compteur de numérotation persisté (factures, reçus).
 *
 * <p>Pourquoi cette table existe : les numéros de facture et de reçu étaient
 * calculés en Java par {@code count() + 1}. Après la suppression d'une ligne (ou
 * une restauration de sauvegarde), le numéro recalculé retombait sur une valeur
 * déjà existence et la contrainte unique rejetait l'enregistrement — un 409
 * « Donnée en conflit » définitif pour le reçu de paie enseignant, qui n'avait
 * aucune boucle de reprise. Un compteur en base, incrémenté sous verrou, ne peut
 * ni collisionner ni régresser.</p>
 */
@Entity
@Table(name = "number_sequences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NumberSequence {

    /** Préfixe du numéro (ex. {@code RCP-}), qui sert de clé. */
    @Id
    @Column(length = 20)
    private String prefix;

    /**
     * Dernière valeur attribuée (0 = aucune).
     *
     * <p>La colonne s'appelle {@code last_seq_value} et non {@code last_value} :
     * ce dernier est un MOT RÉSERVÉ de MySQL 8, ce qui obligerait tout SQL manuel
     * (script d'exploitation, requête de diagnostic, test d'intégration) à
     * l'entourer de backticks — une source d'erreur de syntaxe silencieuse.</p>
     */
    @Column(name = "last_seq_value", nullable = false)
    private long lastValue;

    /** Verrou optimiste : garde-fou supplémentaire en cas d'accès concurrent. */
    @Version
    @Column(nullable = false)
    private long version;
}
