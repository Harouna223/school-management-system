package com.school.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Clôture mensuelle de la paie des enseignants.
 * <p>
 * Une fois un mois clôturé : les heures de ce mois ne peuvent plus être
 * modifiées ni supprimées (sauf réouverture par un rôle autorisé).
 * Les paiements restent possibles sur un mois clôturé.
 */
@Entity
@Table(
        name = "teacher_month_closures",
        uniqueConstraints = @UniqueConstraint(name = "uk_tmc_month", columnNames = {"month_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherMonthClosure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Premier jour du mois concerné (ex. 2026-09-01). */
    @Column(name = "month_date", nullable = false)
    private LocalDate monthDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_year_id")
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by")
    private User closedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reopened_by")
    private User reopenedBy;

    @Column(name = "reopened_at")
    private LocalDateTime reopenedAt;

    @Builder.Default
    @Column(nullable = false)
    private boolean closed = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        if (closedAt == null) {
            closedAt = LocalDateTime.now();
        }
    }
}
