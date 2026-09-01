package com.school.entity;

import com.school.enums.DeliberationStatus;
import com.school.enums.Term;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bulletin trimestriel généré d'un élève.
 */
@Entity
@Table(name = "bulletins")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bulletin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private Term term;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(precision = 5, scale = 2)
    private BigDecimal average;

    @Column(name = "class_average", precision = 5, scale = 2)
    private BigDecimal classAverage;

    @Column(name = "class_min", precision = 5, scale = 2)
    private BigDecimal classMin;

    @Column(name = "class_max", precision = 5, scale = 2)
    private BigDecimal classMax;

    @Column(name = "`rank`")
    private Integer rank;

    @Column(length = 50)
    private String mention;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DeliberationStatus decision;

    @Column(name = "pdf_path", length = 255)
    private String pdfPath;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;
}
