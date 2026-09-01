package com.school.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Élément Constitutif (EC) d'une UE.
 */
@Entity
@Table(name = "course_units", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"ue_id", "code"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Builder.Default
    @Column(nullable = false)
    private int credits = 0;

    @Builder.Default
    @Column(nullable = false)
    private int coefficient = 1;

    /** Volume horaire Cours Magistral. */
    @Column(name = "volume_cm")
    private int volumeCm;

    /** Volume horaire Travaux Dirigés. */
    @Column(name = "volume_td")
    private int volumeTd;

    /** Volume horaire Travaux Pratiques. */
    @Column(name = "volume_tp")
    private int volumeTp;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ue_id")
    private UniversityUnit ue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    private String description;
}