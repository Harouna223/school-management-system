package com.school.entity;

import com.school.enums.LmdDecision;
import com.school.enums.Mention;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Délibération LMD d'un élève pour une filière et un semestre.
 */
@Entity
@Table(name = "lmd_deliberations", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"student_id", "field_id", "semester"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LmdDeliberation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_id")
    private AcademicField field;

    @Column(nullable = false, length = 5)
    private String semester;

    @Builder.Default
    @Column(name = "session_no", nullable = false)
    private int session = 1;

    @Column(precision = 5, scale = 2)
    private BigDecimal average;

    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private LmdDecision decision;

    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private Mention mention;

    @Column(name = "credits_obtained")
    private Integer creditsObtained;

    @Column(name = "credits_failed")
    private Integer creditsFailed;

    @Column(name = "ues_to_retake", length = 500)
    private String uesToRetake;

    @Column(name = "rank_in_class")
    private Integer rankInClass;

    @Builder.Default
    @Column(nullable = false)
    private boolean locked = false;

    @Column(name = "deliberated_at")
    private LocalDateTime deliberatedAt;
}
