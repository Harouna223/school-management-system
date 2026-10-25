package com.school.dto.response;

import com.school.entity.Memoire;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Vue API d'un mémoire universitaire.
 *
 * <p>Comme {@link StageResponse}, les rattachements sont aplatis pour éviter
 * l'exposition des entités JPA (relations paresseuses non sérialisables).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoireResponse {

    private Long id;
    private Long studentId;
    private String studentName;
    private String matricule;
    private String subject;
    private Long directorId;
    private String directorName;
    private LocalDate defenseDate;
    private String jury;
    private String defenseLocation;
    private BigDecimal grade;
    private String decision;
    private String status;
    private LocalDateTime createdAt;

    public static MemoireResponse from(Memoire m) {
        MemoireResponseBuilder builder = MemoireResponse.builder()
                .id(m.getId())
                .subject(m.getSubject())
                .defenseDate(m.getDefenseDate())
                .jury(m.getJury())
                .defenseLocation(m.getDefenseLocation())
                .grade(m.getGrade())
                .decision(m.getDecision())
                .status(m.getStatus())
                .createdAt(m.getCreatedAt());
        if (m.getStudent() != null) {
            builder.studentId(m.getStudent().getId())
                    .studentName(m.getStudent().getFullName())
                    .matricule(m.getStudent().getMatricule());
        }
        if (m.getDirector() != null) {
            builder.directorId(m.getDirector().getId())
                    .directorName(m.getDirector().getFirstName() + " " + m.getDirector().getLastName());
        }
        return builder.build();
    }
}
