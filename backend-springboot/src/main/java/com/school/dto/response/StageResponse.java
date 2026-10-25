package com.school.dto.response;

import com.school.entity.Stage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Vue API d'un stage universitaire.
 *
 * <p>Les rattachements ({@code student}, {@code supervisor}) sont aplatis :
 * exposer l'entité JPA provoquait une {@code LazyInitializationException}
 * (open-in-view désactivé) et divulguait les relations internes de l'élève.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StageResponse {

    private Long id;
    private Long studentId;
    private String studentName;
    private String matricule;
    private String company;
    private String companyContact;
    private String subject;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long supervisorId;
    private String supervisorName;
    private String conventionRef;
    private boolean reportSubmitted;
    private LocalDate defenseDate;
    private BigDecimal grade;
    private String evaluation;
    private String status;
    private LocalDateTime createdAt;

    public static StageResponse from(Stage s) {
        StageResponseBuilder builder = StageResponse.builder()
                .id(s.getId())
                .company(s.getCompany())
                .companyContact(s.getCompanyContact())
                .subject(s.getSubject())
                .startDate(s.getStartDate())
                .endDate(s.getEndDate())
                .conventionRef(s.getConventionRef())
                .reportSubmitted(s.isReportSubmitted())
                .defenseDate(s.getDefenseDate())
                .grade(s.getGrade())
                .evaluation(s.getEvaluation())
                .status(s.getStatus())
                .createdAt(s.getCreatedAt());
        if (s.getStudent() != null) {
            builder.studentId(s.getStudent().getId())
                    .studentName(s.getStudent().getFullName())
                    .matricule(s.getStudent().getMatricule());
        }
        if (s.getSupervisor() != null) {
            builder.supervisorId(s.getSupervisor().getId())
                    .supervisorName(s.getSupervisor().getFirstName() + " " + s.getSupervisor().getLastName());
        }
        return builder.build();
    }
}
