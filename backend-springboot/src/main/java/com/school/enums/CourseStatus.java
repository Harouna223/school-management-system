package com.school.enums;

/**
 * Cycle de vie d'une ressource pédagogique.
 *
 * <ul>
 *   <li>{@code DRAFT} — en préparation : visible de son seul auteur et de
 *       l'administration, jamais d'un élève.</li>
 *   <li>{@code PUBLISHED} — visible des élèves des classes ciblées.</li>
 *   <li>{@code ARCHIVED} — retirée du catalogue sans être supprimée.</li>
 * </ul>
 */
public enum CourseStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
