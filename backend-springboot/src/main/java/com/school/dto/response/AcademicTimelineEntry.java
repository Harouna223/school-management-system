package com.school.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Élément de timeline du parcours académique d'un enfant.
 * Fusionne l'historique scolaire (StudentHistory) et universitaire (EnrollmentHistory).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcademicTimelineEntry {

    private Long id;
    /** SCHOOL ou UNIVERSITY. */
    private String context;
    /** Action (TRANSFERT, RADIATION, LEVEL_CHANGE, ENROLLMENT_STATUS...). */
    private String action;
    private String fromLabel;
    private String toLabel;
    private String academicYear;
    private String reason;
    private String status;
    private LocalDateTime date;
}