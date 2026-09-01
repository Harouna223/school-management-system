package com.school.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Réponse d'enregistrement des présences : liste des présences enregistrées
 * + alertes WhatsApp à ouvrir dans le navigateur (parents des absents/retards).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceRecordResponse {
    private List<AttendanceResponse> attendance;
    private List<WhatsappAlert> alerts;
}