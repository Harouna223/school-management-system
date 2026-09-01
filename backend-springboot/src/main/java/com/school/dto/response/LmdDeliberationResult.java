package com.school.dto.response;

import com.school.entity.LmdDeliberation;
import com.school.enums.LmdDecision;
import com.school.enums.Mention;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Résultat de semestre / délibération LMD d'un étudiant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LmdDeliberationResult {

    private Long studentId;
    private String matricule;
    private String studentName;
    private Long fieldId;
    private String semester;
    private BigDecimal average;
    private LmdDecision decision;
    private Mention mention;
    private Integer creditsObtained;
    private Integer creditsFailed;
    private Integer totalCredits;
    private Integer rankInClass;
    @Builder.Default
    private List<String> uesToRetake = new ArrayList<>();
    @Builder.Default
    private List<String> compensatedUes = new ArrayList<>();

    public static LmdDeliberationResult from(LmdDeliberation d) {
        return LmdDeliberationResult.builder()
                .studentId(d.getStudent().getId())
                .matricule(d.getStudent().getMatricule())
                .studentName(d.getStudent().getFullName())
                .fieldId(d.getField().getId())
                .semester(d.getSemester())
                .average(d.getAverage())
                .decision(d.getDecision())
                .mention(d.getMention())
                .creditsObtained(d.getCreditsObtained())
                .creditsFailed(d.getCreditsFailed())
                .rankInClass(d.getRankInClass())
                .uesToRetake(d.getUesToRetake() != null
                        ? Arrays.asList(d.getUesToRetake().split("\\s*,\\s*"))
                        : new ArrayList<>())
                .build();
    }
}
