package com.school.entity;

import com.school.exception.BusinessException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Heures enseignées par un professeur pour une journée donnée.
 * <p>
 * Le tarif horaire et le montant calculé sont figés au moment de la saisie :
 * toute modification ultérieure du tarif horaire du professeur n'affecte pas
 * les enregistrements existants.
 */
@Entity
@Table(
        name = "teacher_work_hours",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_twh_teacher_date_subject_class",
                columnNames = {"teacher_id", "work_date", "subject_id", "class_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherWorkHour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Column(name = "work_date", nullable = false)
    private LocalDate date;

    /** Nombre d'heures enseignées (ex. 2, 3.5). */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal hours;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id")
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_year_id")
    private AcademicYear academicYear;

    /** Tarif horaire appliqué au moment de la saisie (conservé). */
    @Column(name = "hourly_rate_applied", nullable = false, precision = 12, scale = 2)
    private BigDecimal hourlyRateApplied;

    /** Montant calculé côté serveur : heures × tarif. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 255)
    private String observation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        recalculate();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        recalculate();
    }

    /** Le montant est TOUJOURS recalculé côté serveur : jamais fourni par le client. */
    private void recalculate() {
        if (hours != null && hourlyRateApplied != null) {
            amount = hours.multiply(hourlyRateApplied);
        }
    }

    /** Validation des données saisies. */
    public void validate() {
        if (date == null) {
            throw new BusinessException("La date est obligatoire");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new BusinessException("Impossible d'enregistrer des heures pour une date future");
        }
        if (hours == null || hours.signum() <= 0) {
            throw new BusinessException("Le nombre d'heures doit être strictement positif");
        }
        if (hours.compareTo(new BigDecimal("24")) > 0) {
            throw new BusinessException("Le nombre d'heures ne peut pas dépasser 24 h par jour");
        }
        if (hourlyRateApplied == null || hourlyRateApplied.signum() <= 0) {
            throw new BusinessException("Aucun tarif horaire valide pour cet enseignant à cette date");
        }
    }
}
