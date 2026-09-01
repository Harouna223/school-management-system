package com.school.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Création / modification d'une convocation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConvocationRequest {

    @NotNull(message = "L'élève est obligatoire")
    private Long studentId;

    /** SCHOOL ou UNIVERSITY. */
    @NotBlank(message = "Le contexte est obligatoire")
    private String context;

    @NotBlank(message = "Le motif est obligatoire")
    private String subject;

    private String message;

    @NotNull(message = "La date est obligatoire")
    private LocalDate date;

    @NotNull(message = "L'heure est obligatoire")
    private LocalDateTime time;

    @NotBlank(message = "Le lieu est obligatoire")
    private String location;
}