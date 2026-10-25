package com.school.dto.response;

import com.school.entity.Candidature;
import com.school.enums.CandidatureStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Vue API d'une candidature d'admission.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidatureResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private String email;
    private String phone;
    private String fieldName;
    private String level;
    private String reference;
    private String notes;
    private CandidatureStatus status;
    private LocalDateTime createdAt;

    public static CandidatureResponse from(Candidature c) {
        return CandidatureResponse.builder()
                .id(c.getId())
                .firstName(c.getFirstName())
                .lastName(c.getLastName())
                .birthDate(c.getBirthDate())
                .email(c.getEmail())
                .phone(c.getPhone())
                .fieldName(c.getFieldName())
                .level(c.getLevel())
                .reference(c.getReference())
                .notes(c.getNotes())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
