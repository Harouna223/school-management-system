package com.school.entity;

import com.school.enums.ConvocationStatus;
import com.school.enums.ConvocationTarget;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Convocation scolaire ou universitaire.
 * Peut concerner un élève/étudiant, un parent d'élève ou un enseignant, avec un
 * motif, une date/heure/lieu et un statut.
 */
@Entity
@Table(name = "convocations",
        indexes = {
                @Index(name = "idx_convocation_student", columnList = "student_id"),
                @Index(name = "idx_convocation_parent", columnList = "parent_id"),
                @Index(name = "idx_convocation_teacher", columnList = "teacher_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Convocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String reference;

    /** Destinataire de la convocation (élève, parent ou enseignant). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private ConvocationTarget target = ConvocationTarget.ELEVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Parent parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    @Column(nullable = false, length = 15)
    private String context;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "convocation_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private LocalDateTime time;

    @Column(nullable = false, length = 150)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private ConvocationStatus status = ConvocationStatus.SENT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}