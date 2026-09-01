package com.school.entity;

import com.school.enums.EducationCycle;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "levels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Level {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    /** Cycle d'enseignement (Jardin, Primaire, Collège, Lycée, Université). Nullable = rétrocompatible. */
    @Enumerated(EnumType.STRING)
    @Column(name = "education_cycle", length = 15)
    private EducationCycle educationCycle;
}
