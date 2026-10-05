package com.school.integration;

import com.school.entity.Role;
import com.school.entity.User;
import com.school.repository.PermissionRepository;
import com.school.repository.RoleRepository;
import com.school.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de fumee : verifie que le contexte Spring Boot demarre COMPLETEMENT sur
 * un vrai MySQL, que le schema est cree par Hibernate et que l'initialisation
 * des roles/permissions (DataInitializer) s'execute.
 *
 * <p>C'est le test qui prouve que la chaine complete fonctionne — sans lui,
 * rien d'autre n'est verifie dans un vrai contexte.</p>
 */
class ApplicationBootstrapIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Test
    @DisplayName("le schema Hibernate est cree et le compte administrateur existe")
    void schemaCreeEtAdminInitialise() {
        Optional<User> admin = userRepository.findByUsername("admin");

        assertThat(admin).isPresent();
        assertThat(admin.get().getPassword()).startsWith("$2");
        assertThat(admin.get().getRoles()).isNotEmpty();
    }

    @Test
    @DisplayName("les roles metier (dont ETUDIANT) et leurs permissions sont synchronises")
    void rolesEtPermissionsSynchronises() {
        assertThat(roleRepository.findByName("SUPER_ADMIN")).isPresent();
        assertThat(roleRepository.findByName("ELEVE")).isPresent();
        // Role cree par la migration d'audit : necessaire a l'espace universitaire.
        assertThat(roleRepository.findByName("ETUDIANT")).isPresent();
        assertThat(permissionRepository.count()).isGreaterThan(20);
    }

    @Test
    @DisplayName("un role expose ses permissions fines")
    void roleExposeSesPermissions() {
        Role etudiant = roleRepository.findByName("ETUDIANT").orElseThrow();

        assertThat(etudiant.getPermissions()).isNotEmpty();
        assertThat(etudiant.getPermissions()).anyMatch(p -> "LMD_READ".equals(p.getName()));
    }
}