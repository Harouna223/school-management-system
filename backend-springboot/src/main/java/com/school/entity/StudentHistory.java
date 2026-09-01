package com.school.entity;

import com.school.enums.StudentHistoryAction;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Trace des changements de parcours : transfert, radiation, réinscription.
 */
@Entity
@Table(name = "student_histories",
        indexes = @Index(name = "idx_history_student", columnList = "student_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StudentHistoryAction action;

    @Column(name = "from_class", length = 100)
    private String fromClass;

    @Column(name = "to_class", length = 100)
    private String toClass;

    @Column(length = 500)
    private String reason;

    @Column(name = "recorded_by", length = 100)
    private String recordedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}