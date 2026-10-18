package com.school.enums;

/**
 * Nature du fichier porté par une ressource pédagogique.
 *
 * <p>La valeur est figée en base (colonne {@code enum('VIDEO','PDF')}) :
 * la renommer exigerait une migration.</p>
 */
public enum CourseResourceType {
    VIDEO,
    PDF
}
