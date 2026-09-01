package com.school.entity;

import com.school.enums.ExamStatus;
import com.school.enums.ExamType;
import com.school.enums.Term;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Évaluation planifiée (contrôle, devoir, examen).
 */
@Entity
@Table(name = "exams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExamType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private Term term;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id")
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @Column(name = "exam_date")
    private LocalDate examDate;

    @Builder.Default
    @Column(nullable = false)
    private Integer coefficient = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExamStatus status;
}
