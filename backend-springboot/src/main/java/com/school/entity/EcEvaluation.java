package com.school.entity;

import com.school.enums.EvaluationType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Évaluation d'un EC pour un étudiant (CC, TP, TD, oral, projet, examen).
 * Un EC peut avoir plusieurs évaluations, chaque session ayant ses propres notes.
 */
@Entity
@Table(name = "ec_evaluations", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"ec_id", "student_id", "evaluation_type", "session"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EcEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ec_id")
    private CourseUnit ec;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_type", nullable = false, length = 15)
    private EvaluationType evaluationType;

    @Builder.Default
    @Column(nullable = false)
    private int session = 1;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal value;

    @Column(name = "max_value", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxValue;

    @Column(length = 100)
    private String appreciation;
}