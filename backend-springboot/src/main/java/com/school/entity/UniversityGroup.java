package com.school.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "university_groups", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"field_id", "level", "code"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UniversityGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 20)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_id")
    private AcademicField field;

    @Column(length = 10)
    private String level;

    @Column(name = "academic_year", length = 20)
    private String academicYear;

    @Column(name = "student_count")
    private Integer studentCount;

    private String description;
}