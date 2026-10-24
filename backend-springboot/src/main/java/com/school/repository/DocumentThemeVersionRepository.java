package com.school.repository;

import com.school.entity.DocumentThemeVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DocumentThemeVersionRepository extends JpaRepository<DocumentThemeVersion, Long> {

    Optional<DocumentThemeVersion> findFirstByActiveTrueOrderByIdDesc();

    List<DocumentThemeVersion> findAllByOrderByIdDesc();

    long countByActiveTrue();

    /** Désactive toutes les versions avant d'activer la nouvelle (une seule active). */
    @Modifying
    @Query("update DocumentThemeVersion v set v.active = false where v.active = true")
    int deactivateAll();
}
