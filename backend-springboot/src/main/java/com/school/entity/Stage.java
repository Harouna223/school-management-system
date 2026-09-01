package com.school.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Stage universitaire : entreprise, convention, encadrant, période,
 * rapport, soutenance et évaluation.
 */
@Entity
@Table(name = "stages",
        indexes = @Index(name = "idx_stage_student", columnList = "student_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @Column(nullable = false, length = 150)
    private String company;

    @Column(length = 150)
    private String companyContact;

    @Column(length = 200)
    private String subject;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_id")
    private Teacher supervisor;

    @Column(name = "convention_ref", length = 50)
    private String conventionRef;

    @Column(name = "report_submitted")
    private boolean reportSubmitted;

    @Column(name = "defense_date")
    private LocalDate defenseDate;

    @Column(precision = 5, scale = 2)
    private BigDecimal grade;

    @Column(columnDefinition = "TEXT")
    private String evaluation;

    @Column(length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}