package com.school.dto.response;

import com.school.enums.LmdDecision;
import com.school.enums.Mention;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Données d'une attestation de réussite universitaire PDF avec QR code.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LmdAttestationResponse {

    private String studentName;
    private String matricule;
    private String fieldName;
    private String programName;
    private String diploma;
    private String academicYear;
    private String semester;
    private BigDecimal average;
    private Mention mention;
    private LmdDecision decision;
    private String verificationToken;

    /**
     * Informations portées par un jeton d'attestation valide (vérification externe).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttestationInfo {
        private String matricule;
        private String semester;
        private int session;
        private Instant expiresAt;
    }
}