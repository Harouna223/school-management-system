package com.school.entity;

import com.school.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transaction de paiement (total ou partiel) du salaire d'un enseignant.
 * Chaque paiement est enregistré séparément avec son numéro de reçu unique
 * et sa dépense comptable associée (id stocké pour éviter les doublons).
 */
@Entity
@Table(name = "teacher_payment_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherPaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "monthly_payment_id", nullable = false)
    private TeacherMonthlyPayment monthlyPayment;

    /** Numéro de reçu unique : REC-ENS-YYMMDD-XXXXX. */
    @Column(name = "receipt_no", nullable = false, unique = true, length = 30)
    private String receiptNo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Column(name = "payment_date", nullable = false)
    private LocalDateTime paymentDate;

    @Column(length = 100)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by")
    private User receivedBy;

    @Column(length = 255)
    private String observation;

    /** Identifiant de la dépense comptable générée (null si non intégrée). */
    @Column(name = "expense_id")
    private Long expenseId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        if (paymentDate == null) {
            paymentDate = LocalDateTime.now();
        }
    }
}
