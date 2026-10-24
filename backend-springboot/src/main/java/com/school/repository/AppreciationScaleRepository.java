package com.school.repository;

import com.school.entity.AppreciationScale;
import com.school.enums.EducationCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppreciationScaleRepository extends JpaRepository<AppreciationScale, Long> {

    /** Barème actif d'un cycle (un seul possible), avec ses intervalles. */
    @Query("""
            SELECT DISTINCT s FROM AppreciationScale s
            LEFT JOIN FETCH s.bands
            WHERE s.cycle = :cycle AND s.active = true
            """)
    Optional<AppreciationScale> findActiveWithBands(@Param("cycle") EducationCycle cycle);

    @Query("""
            SELECT DISTINCT s FROM AppreciationScale s
            LEFT JOIN FETCH s.bands
            WHERE s.id = :id
            """)
    Optional<AppreciationScale> findWithBandsById(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT s FROM AppreciationScale s
            LEFT JOIN FETCH s.bands
            ORDER BY s.cycle ASC, s.version DESC
            """)
    List<AppreciationScale> findAllWithBands();

    @Query("""
            SELECT DISTINCT s FROM AppreciationScale s
            LEFT JOIN FETCH s.bands
            WHERE s.cycle = :cycle
            ORDER BY s.version DESC
            """)
    List<AppreciationScale> findByCycleWithBands(@Param("cycle") EducationCycle cycle);

    boolean existsByCycleAndActiveTrue(EducationCycle cycle);

    boolean existsByCycleAndVersion(EducationCycle cycle, int version);
}
