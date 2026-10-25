package com.school.dto.response;

import com.school.entity.Alumnus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Vue API d'un diplômé (alumni).
 *
 * <p>Seul l'identifiant de l'élève rattaché est exposé : lire un identifiant
 * sur un proxy ne déclenche aucun chargement, contrairement à ses autres
 * champs (source de {@code LazyInitializationException}).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlumnusResponse {

    private Long id;
    private Long studentId;
    private String firstName;
    private String lastName;
    private String fieldName;
    private String diploma;
    private String academicYear;
    private String email;
    private String phone;
    private String currentJob;
    private String company;
    private String notes;
    private LocalDateTime createdAt;

    public static AlumnusResponse from(Alumnus a) {
        return AlumnusResponse.builder()
                .id(a.getId())
                .studentId(a.getStudent() != null ? a.getStudent().getId() : null)
                .firstName(a.getFirstName())
                .lastName(a.getLastName())
                .fieldName(a.getFieldName())
                .diploma(a.getDiploma())
                .academicYear(a.getAcademicYear())
                .email(a.getEmail())
                .phone(a.getPhone())
                .currentJob(a.getCurrentJob())
                .company(a.getCompany())
                .notes(a.getNotes())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
