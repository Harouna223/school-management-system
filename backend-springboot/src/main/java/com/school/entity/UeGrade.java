package com.school.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Note d'une UE pour un étudiant, à un semestre et une session (1 = normale, 2 = rattrapage).
 */
@Entity
@Table(name = "ue_grades", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"student_id", "ue_id", "semester", "session"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UeGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ue_id")
    private UniversityUnit ue;

    @Column(nullable = false, length = 5)
    private String semester;

    /** Session : 1 = normale, 2 = rattrapage. */
    @Builder.Default
    @Column(nullable = false)
    private int session = 1;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal value;

    @Column(length = 100)
    private String appreciation;
}
