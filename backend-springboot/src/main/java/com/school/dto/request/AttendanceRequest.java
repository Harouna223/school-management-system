package com.school.dto.request;

import com.school.enums.AttendanceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceRequest {

    @NotNull(message = "La classe est obligatoire")
    private Long classId;

    @NotNull(message = "La date est obligatoire")
    private LocalDate date;

    // @Valid est INDISPENSABLE : sans lui, les contraintes des éléments de la
    // liste ne sont jamais évaluées (Bean Validation ne descend pas dans une
    // collection sans cette annotation). Un pointage `entries:[{}]` provoquait
    // alors findById(null) -> 500, ou un statut nul -> violation SQL -> 409.
    @NotNull(message = "Les pointages sont obligatoires")
    @Valid
    private List<Entry> entries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {
        @NotNull(message = "L'élève est obligatoire")
        private Long studentId;

        @NotNull(message = "Le statut est obligatoire")
        private AttendanceStatus status;

        private String justification;
    }
}