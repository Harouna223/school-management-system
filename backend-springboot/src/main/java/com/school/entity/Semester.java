package com.school.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Semestre universitaire rattaché à une filière et un niveau.
 */
@Entity
@Table(name = "semesters", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"field_id", "code"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Semester {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Code du semestre : S1 → S12. */
    @Column(nullable = false, length = 5)
    private String code;

    /** Libellé (ex. « Semestre 1 — L1 »). */
    @Column(nullable = false, length = 80)
    private String label;

    @Column(nullable = false)
    private int orderIndex;

    @Column(name = "academic_year", length = 20)
    private String academicYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_id")
    private AcademicField field;
}
