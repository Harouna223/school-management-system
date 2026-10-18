package com.school.repository;

import com.school.entity.CourseResource;
import com.school.enums.CourseResourceType;
import com.school.enums.CourseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface CourseResourceRepository extends JpaRepository<CourseResource, Long> {

    /**
     * Catalogue du personnel (administration, enseignants) : toutes les
     * ressources, tous statuts confondus.
     *
     * <p>{@code :classId} passe par un {@code EXISTS} plutôt qu'une jointure :
     * sans cela, une ressource visant trois classes apparaîtrait trois fois dans
     * la pagination, ce qui fausse {@code totalElements}.</p>
     */
    @Query("""
            SELECT r FROM CourseResource r
            WHERE (:search IS NULL OR :search = '' OR
                   LOWER(r.title) LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(COALESCE(r.description, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(COALESCE(r.chapter, '')) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:type IS NULL OR r.type = :type)
              AND (:status IS NULL OR r.status = :status)
              AND (:subjectId IS NULL OR r.subject.id = :subjectId)
              AND (:authorId IS NULL OR r.author.id = :authorId)
              AND (:classId IS NULL OR EXISTS (
                     SELECT 1 FROM r.targetClasses c WHERE c.id = :classId))
            """)
    Page<CourseResource> searchForStaff(@Param("search") String search,
                                        @Param("type") CourseResourceType type,
                                        @Param("status") CourseStatus status,
                                        @Param("subjectId") Long subjectId,
                                        @Param("classId") Long classId,
                                        @Param("authorId") Long authorId,
                                        Pageable pageable);

    /**
     * Catalogue d'un élève : uniquement les ressources PUBLIÉES visant au moins
     * une de ses classes. Le statut est un paramètre explicite (jamais un
     * littéral JPQL) pour que le filtre reste visible à l'appelant.
     */
    @Query("""
            SELECT DISTINCT r FROM CourseResource r
            JOIN r.targetClasses c
            WHERE c.id IN :classIds
              AND r.status = :status
              AND (:search IS NULL OR :search = '' OR
                   LOWER(r.title) LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(COALESCE(r.description, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(COALESCE(r.chapter, '')) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:type IS NULL OR r.type = :type)
              AND (:subjectId IS NULL OR r.subject.id = :subjectId)
            """)
    Page<CourseResource> searchForClasses(@Param("classIds") Collection<Long> classIds,
                                          @Param("status") CourseStatus status,
                                          @Param("search") String search,
                                          @Param("type") CourseResourceType type,
                                          @Param("subjectId") Long subjectId,
                                          Pageable pageable);

    /**
     * Catalogue d'un enseignant : les ressources publiées (catalogue commun)
     * PLUS ses propres brouillons et archives, qu'il doit pouvoir reprendre.
     */
    @Query("""
            SELECT r FROM CourseResource r
            WHERE (:search IS NULL OR :search = '' OR
                   LOWER(r.title) LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(COALESCE(r.description, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(COALESCE(r.chapter, '')) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:type IS NULL OR r.type = :type)
              AND (:status IS NULL OR r.status = :status)
              AND (:subjectId IS NULL OR r.subject.id = :subjectId)
              AND (:classId IS NULL OR EXISTS (
                     SELECT 1 FROM r.targetClasses c WHERE c.id = :classId))
              AND (r.status = :publishedStatus OR r.author.id = :authorId)
            """)
    Page<CourseResource> searchForTeacher(@Param("search") String search,
                                          @Param("type") CourseResourceType type,
                                          @Param("status") CourseStatus status,
                                          @Param("subjectId") Long subjectId,
                                          @Param("classId") Long classId,
                                          @Param("authorId") Long authorId,
                                          @Param("publishedStatus") CourseStatus publishedStatus,
                                          Pageable pageable);

    /** Compte les ressources visant une classe (garde avant suppression de classe). */
    long countByTargetClassesId(Long classId);

    /** Compte les ressources d'une matière (garde informative). */
    long countBySubjectId(Long subjectId);
}
