package com.school.repository;

import com.school.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Chargement d'authentification : rôles et permissions sont nécessaires pour
     * construire les autorités à CHAQUE requête, donc chargés ici en une seule
     * requête (les collections sont désormais {@code LAZY} sur l'entité).
     */
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    Page<User> findByUsernameContainingOrFirstNameContainingOrLastNameContaining(
            String username, String firstName, String lastName, Pageable pageable);
}
