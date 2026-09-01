package com.school.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Matière enseignée avec coefficient.
 */
@Entity
@Table(name = "subjects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Builder.Default
    @Column(nullable = false)
    private Integer coefficient = 1;

    @Column(length = 255)
    private String description;
}
