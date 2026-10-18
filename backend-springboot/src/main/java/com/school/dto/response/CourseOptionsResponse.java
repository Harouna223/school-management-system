package com.school.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Options proposées dans le formulaire de publication d'un cours.
 *
 * <p>Pour un administrateur, {@code restricted} vaut {@code false} et toutes
 * les classes / matières sont proposées. Pour un enseignant, {@code restricted}
 * vaut {@code true} et seules les paires (classe, matière) réellement affectées
 * apparaissent — le serveur revalide de toute façon chaque paire à
 * l'enregistrement.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseOptionsResponse {

    private boolean restricted;
    private List<Option> classes;
    private List<Option> subjects;
    private List<Assignment> assignments;

    public record Option(Long id, String name) {
    }

    public record Assignment(Long classId, Long subjectId) {
    }
}
