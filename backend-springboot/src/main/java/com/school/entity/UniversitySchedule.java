package com.school.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

/**
 * Créneau d'emploi du temps universitaire : cours magistral, TD ou TP d'un EC,
 * pour un groupe, dans une salle, avec un enseignant, sur un semestre.
 */
@Entity
@Table(name = "university_schedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UniversitySchedule {

    public enum SessionType { CM, TD, TP }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_unit_id")
    private CourseUnit courseUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(nullable = false, length = 5)
    private String semester;

    @Column(nullable = false)
    private int dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    /** Groupe / sous-groupe (ex. « A », « TP1 »). */
    @Column(length = 50)
    private String groupName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private SessionType sessionType;
}
