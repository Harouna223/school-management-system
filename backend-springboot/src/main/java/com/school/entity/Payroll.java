package com.school.entity;

import com.school.enums.PayrollStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Bulletin de paie mensuel d'un enseignant.
 */
@Entity
@Table(name = "payrolls", uniqueConstraints = @UniqueConstraint(name = "uk_payrolls_teacher_month", columnNames = {"teacher_id", "month_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payroll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    @Column(name = "month_date", nullable = false)
    private LocalDate monthDate;

    @Column(name = "base_salary", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseSalary;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal allowances;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal deductions;

    @Column(name = "net_salary", nullable = false, precision = 12, scale = 2)
    private BigDecimal netSalary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private PayrollStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /**
     * Dépense comptable générée lors du paiement du bulletin.
     *
     * <p>Sans ce lien, les salaires versés depuis le module RH n'apparaissaient
     * dans AUCUNE dépense : les totaux du tableau de bord et le rapport financier
     * sous-estimaient les charges salariales, alors que la paie à l'heure
     * (module heures) créait bien sa dépense.</p>
     */
    @Column(name = "expense_id")
    private Long expenseId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}