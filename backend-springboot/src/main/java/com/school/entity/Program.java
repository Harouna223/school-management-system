package com.school.entity;

import com.school.enums.UniversityLevel;
import jakarta.persistence.*;
import lombok.*;

/**
 * Programme de formation universitaire (ex. Licence Informatique, Master Gestion).
 */
@Entity
@Table(name = "programs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Program {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    /** Diplôme délivré (Licence, Master, Doctorat...). */
    @Column(nullable = false, length = 50)
    private String diploma;

    /** Durée du programme en années. */
    @Column(nullable = false)
    private int duration;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_id")
    private AcademicField field;

    /** Année académique de la version courante du programme (ex. 2026-2027). */
    @Column(length = 20)
    private String academicYear;

    /** Crédits ECTS requis pour obtenir le diplôme. */
    @Column(name = "total_credits")
    private Integer totalCredits;

    /** Conditions d'admission (texte libre). */
    @Column(name = "admission_requirements", columnDefinition = "TEXT")
    private String admissionRequirements;

    @Column(columnDefinition = "TEXT")
    private String description;
}
