package com.school.dto.request;

import com.school.enums.AttendanceStatus;
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

    @NotNull(message = "Les pointages sont obligatoires")
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