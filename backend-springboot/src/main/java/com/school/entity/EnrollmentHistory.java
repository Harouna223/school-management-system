package com.school.entity;

import com.school.enums.EnrollmentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Trace de l'historique universitaire : changement de niveau, de filière,
 * de programme, de statut ou d'année académique.
 */
@Entity
@Table(name = "enrollment_histories",
        indexes = @Index(name = "idx_enroll_hist_student", columnList = "student_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrollmentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_id")
    private AcademicField field;

    @Column(length = 10)
    private String fromLevel;

    @Column(length = 10)
    private String toLevel;

    @Column(name = "from_semester", length = 5)
    private String fromSemester;

    @Column(name = "to_semester", length = 5)
    private String toSemester;

    @Column(name = "academic_year", length = 20)
    private String academicYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "enrollment_status", length = 15)
    private EnrollmentStatus enrollmentStatus;

    @Column(length = 500)
    private String reason;

    @Column(name = "recorded_by", length = 100)
    private String recordedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}