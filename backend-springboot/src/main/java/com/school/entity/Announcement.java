package com.school.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Annonce administrative diffusée aux utilisateurs (ciblée par rôle ou globale).
 */
@Entity
@Table(name = "announcements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    /**
     * Contenu long de l'annonce (mediumtext en base).
     *
     * <p>{@code @Lob} déclarait un CLOB : Hibernate attendait alors un type
     * CLOB et refusait {@code mediumtext} (LONGVARCHAR) à la validation du
     * schéma — le démarrage échouait. {@code LONGVARCHAR} est le type JDBC
     * réellement rapporté par MySQL pour tinytext/text/mediumtext/longtext :
     * la correspondance est exacte et la taille reste gérée par la colonne.</p>
     */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false)
    private String content;

    @Column(name = "target_role", length = 20)
    private String targetRole;

    @Builder.Default
    @Column(nullable = false)
    private boolean pinned = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}