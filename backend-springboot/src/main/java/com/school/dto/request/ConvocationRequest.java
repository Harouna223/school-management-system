package com.school.dto.request;

import com.school.enums.ConvocationTarget;
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
 *
 * <p>La {@code target} désigne QUI est convoqué ; l'identifiant correspondant
 * est alors obligatoire (élève, parent ou enseignant). {@code target} absente
 * vaut {@code ELEVE}, pour rester compatible avec les appels existants.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConvocationRequest {

    /** ÉLÈVE (défaut), PARENT ou ENSEIGNANT. */
    private ConvocationTarget target;

    /** Obligatoire si {@code target = ELEVE}. */
    private Long studentId;

    /** Obligatoire si {@code target = PARENT}. */
    private Long parentId;

    /** Obligatoire si {@code target = ENSEIGNANT}. */
    private Long teacherId;

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