package com.school.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Intervalle d'un barème d'appréciation.
 *
 * <p>Sémantique sans ambiguïté : <b>borne basse incluse, borne haute exclue</b>
 * ({@code [lowerBound, upperBound)}), sauf le dernier intervalle du barème qui
 * inclut la borne haute de l'échelle. 18,00 appartient donc à « Excellent » et
 * 17,99 à « Très bien ».</p>
 */
@Entity
@Table(name = "appreciation_bands")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppreciationBand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scale_id", nullable = false)
    private AppreciationScale scale;

    @Column(name = "lower_bound", nullable = false, precision = 5, scale = 2)
    private BigDecimal lowerBound;

    @Column(name = "upper_bound", nullable = false, precision = 5, scale = 2)
    private BigDecimal upperBound;

    @Column(nullable = false, length = 60)
    private String label;

    /** Observation pédagogique facultative associée à l'appréciation. */
    @Column(length = 255)
    private String observation;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;
}
