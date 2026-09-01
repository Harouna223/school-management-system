package com.school.entity;

import com.school.enums.UeType;
import jakarta.persistence.*;
import lombok.*;

/**
 * Unité d'Enseignement (UE) d'une filière, rattachée à un semestre.
 * Peut contenir des Éléments Constitutifs (EC).
 */
@Entity
@Table(name = "university_units")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UniversityUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Builder.Default
    @Column(nullable = false)
    private int coefficient = 1;

    @Builder.Default
    @Column(nullable = false)
    private int credits = 0;

    /** Semestre (S1 → S12). */
    @Column(nullable = false, length = 5)
    private String semester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_id")
    private AcademicField field;

    /** Type d'UE (fondamentale, complémentaire, transversale, libre). */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UeType type;

    /** UE facultative (optionnelle) : sa validation n'est pas requise pour la moyenne. */
    @Builder.Default
    @Column(name = "optional_ue")
    private boolean optionalUe = false;

    private String description;
}
