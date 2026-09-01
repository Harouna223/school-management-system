package com.school.entity;

import com.school.enums.EnrollmentStatus;
import com.school.enums.UniversityLevel;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Inscription d'un étudiant dans une filière LMD, enrichie :
 * programme, niveau, année académique, statut d'inscription.
 */
@Entity
@Table(name = "lmd_enrollments", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"student_id", "field_id"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LmdEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_id")
    private AcademicField field;

    @Column(name = "current_semester", nullable = false, length = 5)
    private String currentSemester;

    /** Niveau universitaire (L1, L2, L3, M1, M2). */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private UniversityLevel level;

    /** Programme de formation suivi. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id")
    private Program program;

    /** Année académique de l'inscription (ex. 2026-2027). */
    @Column(length = 20)
    private String academicYear;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "enrollment_status", length = 15)
    private EnrollmentStatus enrollmentStatus;

    @Column(name = "enrolled_at", updatable = false)
    private LocalDateTime enrolledAt;

    @PrePersist
    public void prePersist() {
        enrolledAt = LocalDateTime.now();
        if (enrollmentStatus == null) {
            enrollmentStatus = EnrollmentStatus.INSCRIT;
        }
    }
}
