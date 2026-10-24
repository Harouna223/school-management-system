package com.school.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Version d'une configuration de présentation des documents (thème global).
 *
 * <p>Une ligne = une configuration <b>enregistrée</b>. Les colonnes
 * {@code config_json} ne sont plus modifiées après création, sauf le drapeau
 * {@code active} : l'historique reste donc fidèle, et réactiver une version
 * antérieure revient exactement à l'état qu'elle décrivait.</p>
 *
 * <p>L'établissement est mono-tenant : aucune colonne {@code school_id} n'est
 * introduite. Le thème est global à l'installation, comme les paramètres.</p>
 */
@Entity
@Table(name = "document_theme_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentThemeVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(nullable = false, length = 20)
    private String template;

    @Column(length = 40)
    private String palette;

    @Column(name = "config_json", nullable = false, columnDefinition = "JSON")
    private String configJson;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;
}
