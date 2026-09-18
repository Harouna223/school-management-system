package com.school.dto.response;

import com.school.enums.TeacherPaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ligne du tableau mensuel « Paie des enseignants » :
 * heures du mois, tarif appliqué, salaire calculé, payé, restant, statut.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherPayrollRowResponse {

    private Long teacherId;
    private String teacherName;
    private Long monthlyPaymentId;
    private BigDecimal totalHours;
    private BigDecimal hourlyRate;
    private BigDecimal totalAmount;
    private BigDecimal amountPaid;
    private BigDecimal remainingAmount;
    private TeacherPaymentStatus status;
    private boolean closed;
}
