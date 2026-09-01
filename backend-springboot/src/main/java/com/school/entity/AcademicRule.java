package com.school.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Règle académique configurable pour un cycle d'enseignement.
 * Permet de paramétrer les seuils de validation, compensation, mentions, etc.
 * sans coder en dur dans le service.
 */
@Entity
@Table(name = "academic_rules", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"cycle", "rule_key"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Cycle concerné (JARDIN, PRIMAIRE, COLLEGE, LYCEE, UNIVERSITE) ou null pour tous. */
    @Column(length = 15)
    private String cycle;

    @Column(name = "rule_key", nullable = false, length = 50)
    private String ruleKey;

    @Column(name = "rule_value", nullable = false, length = 100)
    private String ruleValue;

    @Column(length = 255)
    private String description;
}