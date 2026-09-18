package com.school.entity;

import com.school.exception.BusinessException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tarif horaire d'un enseignant, historisé par période de validité.
 * <p>
 * Un tarif dont la date de fin est passée reste en base : les heures saisies
 * pendant sa période de validité conservent le tarif appliqué au moment de la
 * saisie (colonne {@code hourly_rate_applied} de {@link TeacherWorkHour}),
 * donc changer le tarif ne recalcule jamais les anciens salaires.
 */
@Entity
@Table(name = "teacher_hourly_rates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherHourlyRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Column(name = "hourly_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_year_id")
    private AcademicYear academicYear;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Le tarif est-il applicable à la date donnée ? */
    public boolean covers(LocalDate date) {
        return active && !startDate.isAfter(date) && (endDate == null || !endDate.isBefore(date));
    }

    /** Validation métier du couple de dates. */
    public void validateDates() {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException("La date de fin de validité doit être postérieure à la date de début");
        }
        if (hourlyRate == null || hourlyRate.signum() <= 0) {
            throw new BusinessException("Le tarif horaire doit être strictement positif");
        }
    }
}
