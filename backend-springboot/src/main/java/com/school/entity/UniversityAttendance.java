package com.school.entity;

import com.school.enums.AttendanceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Présence universitaire : pointage d'un étudiant à une séance d'un EC
 * (CM, TD, TP), pour un groupe éventuel, à une date donnée.
 */
@Entity
@Table(name = "university_attendances", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"ec_id", "student_id", "attendance_date", "session_type"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UniversityAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ec_id")
    private CourseUnit ec;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private AttendanceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 5)
    private com.school.entity.UniversitySchedule.SessionType sessionType;

    @Column(name = "group_name", length = 50)
    private String groupName;

    @Column(length = 255)
    private String justification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}