package com.school.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Note d'un Élément Constitutif (EC) pour un étudiant, à une session (1 = normale, 2 = rattrapage).
 */
@Entity
@Table(name = "ec_grades", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"student_id", "course_unit_id", "session"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EcGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_unit_id")
    private CourseUnit courseUnit;

    /** Session : 1 = normale, 2 = rattrapage. */
    @Builder.Default
    @Column(nullable = false)
    private int session = 1;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal value;

    @Column(length = 100)
    private String appreciation;
}
