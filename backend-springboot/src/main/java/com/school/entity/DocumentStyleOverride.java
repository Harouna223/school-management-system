package com.school.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Configuration de présentation propre à un TYPE de document (niveau 2).
 *
 * <p>Le thème global s'applique à tous les documents. Une ligne ici ne
 * <b>remplace</b> pas ce thème : elle ajoute des valeurs ponctuelles, et
 * seules les clés présentes sont prises en compte. Un bulletin peut donc être
 * en paysage sans que l'apparence des reçus change.</p>
 */
@Entity
@Table(name = "document_style_overrides")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentStyleOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_type", nullable = false, length = 40)
    private String documentType;

    @Column(name = "config_json", nullable = false, columnDefinition = "JSON")
    private String configJson;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
