package com.school.repository;

import com.school.entity.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Accès aux rôles. Les permissions sont {@code LAZY} sur l'entité : elles sont
 * chargées ici car la synchronisation de démarrage ({@code DataInitializer}) et
 * la création de comptes lisent les permissions hors transaction.
 */
public interface RoleRepository extends JpaRepository<Role, Long> {

    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    /**
     * Recherche insensible a la casse : le role cible d'une annonce vient d'un formulaire et
     * peut arriver en minuscules ("enseignant"). Sans cette methode, la comparaison dependrait
     * de la collation MySQL et echouerait silencieusement sur une base sensible a la casse.
     */
    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findByNameIgnoreCase(String name);
}
