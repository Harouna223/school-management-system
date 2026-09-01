package com.school.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Inscription d'un étudiant à une UE (obligatoire ou optionnelle) pour un semestre.
 */
@Entity
@Table(name = "ue_enrollments", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"student_id", "ue_id"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UeEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ue_id")
    private UniversityUnit ue;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "enrolled_at", updatable = false)
    private LocalDateTime enrolledAt;

    @PrePersist
    public void prePersist() {
        enrolledAt = LocalDateTime.now();
    }
}
