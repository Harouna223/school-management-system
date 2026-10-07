package com.school.repository;

import com.school.entity.AcademicField;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository des filières académiques.
 */
@Repository
public interface AcademicFieldRepository extends JpaRepository<AcademicField, Long> {

    /**
     * PHASE 7 (open-in-view=false) : department, domain sont LAZY et
     * department.faculty (bien qu'EAGER) devient LAZY sous un fetch graph
     * (sémantique JPA : les attributs hors graph sont traités comme LAZY) —
     * l'entité est sérialisée telle quelle par /api/lmd/fields HORS transaction.
     * Le graph complet initialise toute la chaîne pendant la transaction ->
     * plus de LazyInitializationException. Faculty et Domain sont des entités
     * feuilles (aucune association) : la récursion s'arrête là.
     */
    @EntityGraph(attributePaths = {"department", "department.faculty", "domain"})
    List<AcademicField> findByDepartmentIdOrderByName(Long departmentId);

    /** Variante de findAll() pour la sérialisation directe (GET /api/lmd/fields). */
    @Override
    @EntityGraph(attributePaths = {"department", "department.faculty", "domain"})
    List<AcademicField> findAll();

    boolean existsByName(String name);

    boolean existsByCode(String code);

    List<AcademicField> findTop8ByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(String name, String code);
}
