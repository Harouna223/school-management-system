package com.school.entity;

import com.school.enums.TeacherPaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Paiement mensuel d'un enseignant, calculé à partir des heures enseignées.
 * <p>
 * Formule : totalAmount = somme des montants des heures du mois
 * (chaque saisie conservant son propre tarif appliqué).
 * Les paiements peuvent être complets ou partiels ({@link TeacherPaymentStatus#PENDING PARTIAL/PAID}).
 */
@Entity
@Table(
        name = "teacher_monthly_payments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tmp_teacher_month",
                columnNames = {"teacher_id", "month_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherMonthlyPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    /** Premier jour du mois concerné (ex. 2026-09-01). */
    @Column(name = "month_date", nullable = false)
    private LocalDate monthDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_year_id")
    private AcademicYear academicYear;

    @Column(name = "total_hours", nullable = false, precision = 8, scale = 2)
    private BigDecimal totalHours;

    /** Tarif horaire de référence (dernier tarif appliqué dans le mois). */
    @Column(name = "hourly_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "amount_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid;

    @Column(name = "remaining_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal remainingAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TeacherPaymentStatus status;

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
}
