package com.school.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Ligne d'un rapport de présences par élève sur une période.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceReportRow {

    private Long studentId;
    private String matricule;
    private String lastName;
    private String firstName;
    private long present;
    private long absent;
    private long late;
    private long justified;
    private BigDecimal rate;
}
