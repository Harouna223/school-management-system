package com.school.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Épreuve d'examen universitaire planifiée : un EC examiné à une date/heure,
 * dans une salle, pour un groupe éventuel, sur une session (1 normale / 2 rattrapage).
 */
@Entity
@Table(name = "university_exams",
        indexes = @Index(name = "idx_univ_exam_ec", columnList = "course_unit_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UniversityExam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_unit_id")
    private CourseUnit courseUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_id")
    private Teacher supervisor;

    @Column(name = "exam_date", nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Builder.Default
    @Column(nullable = false)
    private int session = 1;

    @Column(name = "group_name", length = 50)
    private String groupName;

    @Column(length = 20)
    private String semester;

    @Column(length = 255)
    private String notes;
}