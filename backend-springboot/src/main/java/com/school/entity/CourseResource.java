package com.school.entity;

import com.school.enums.CourseResourceType;
import com.school.enums.CourseStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Ressource pédagogique (vidéo ou PDF) publiée depuis SAHEL EDU.
 *
 * <p>Le binaire n'est jamais stocké en base : {@link #storageFilename} porte le
 * chemin RELATIF sous {@code ${UPLOAD_DIR}/courses/}. Le nom physique est
 * généré côté serveur (UUID), jamais dérivé du nom fourni par l'utilisateur.</p>
 *
 * <p>{@code targetClasses} est une collection de classes : une même ressource
 * peut viser plusieurs classes sans dupliquer ni le fichier ni les
 * métadonnées.</p>
 */
@Entity
@Table(name = "course_resources")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    /** Chapitre ou partie du programme (facultatif). */
    @Column(length = 150)
    private String chapter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CourseResourceType type;

    /** Nom d'origine, uniquement informatif (jamais utilisé pour un chemin). */
    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    /** Chemin relatif sous {@code ${UPLOAD_DIR}} : {@code courses/<id>/<uuid>.<ext>}. */
    @Column(name = "storage_filename", length = 255, unique = true)
    private String storageFilename;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private CourseStatus status = CourseStatus.DRAFT;

    /**
     * Autorise le téléchargement du fichier. Distinct de la consultation :
     * un établissement peut vouloir diffuser une vidéo en lecture seule.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean downloadable = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    /** Compte ayant créé la ressource (élément de preuve et de propriété). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "course_resource_classes",
            joinColumns = @JoinColumn(name = "resource_id"),
            inverseJoinColumns = @JoinColumn(name = "class_id"))
    @Builder.Default
    private Set<SchoolClass> targetClasses = new LinkedHashSet<>();

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = CourseStatus.DRAFT;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Vrai si un fichier a déjà été téléversé. */
    public boolean hasFile() {
        return storageFilename != null && !storageFilename.isBlank();
    }
}
