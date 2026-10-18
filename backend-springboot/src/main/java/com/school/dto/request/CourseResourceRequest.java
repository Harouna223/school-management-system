package com.school.dto.request;

import com.school.enums.CourseResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Création / modification des métadonnées d'une ressource pédagogique.
 *
 * <p>Le fichier n'est pas envoyé ici : il fait l'objet d'un appel multipart
 * séparé ({@code POST /api/courses/{id}/file}). Cela permet d'enregistrer un
 * brouillon avant l'upload, et de remplacer un fichier sans retoucher les
 * métadonnées.</p>
 */
@Data
public class CourseResourceRequest {

    @NotBlank(message = "Le titre est obligatoire")
    @Size(max = 200, message = "Le titre ne peut pas dépasser 200 caractères")
    private String title;

    @Size(max = 2000, message = "La description ne peut pas dépasser 2000 caractères")
    private String description;

    @Size(max = 150, message = "Le chapitre ne peut pas dépasser 150 caractères")
    private String chapter;

    @NotNull(message = "Le type (VIDEO ou PDF) est obligatoire")
    private CourseResourceType type;

    @NotNull(message = "La matière est obligatoire")
    private Long subjectId;

    @NotEmpty(message = "Au moins une classe destinataire est obligatoire")
    @Size(max = 50, message = "Une ressource ne peut pas viser plus de 50 classes")
    private List<Long> classIds;

    /** Valeur par défaut : téléchargement autorisé. */
    private Boolean downloadable;
}
