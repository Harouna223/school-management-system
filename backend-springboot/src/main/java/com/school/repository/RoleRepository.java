package com.school.repository;

import com.school.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    /**
     * Recherche insensible a la casse : le role cible d'une annonce vient d'un formulaire et
     * peut arriver en minuscules ("enseignant"). Sans cette methode, la comparaison dependrait
     * de la collation MySQL et echouerait silencieusement sur une base sensible a la casse.
     */
    Optional<Role> findByNameIgnoreCase(String name);
}
