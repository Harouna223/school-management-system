package com.school.repository;

import com.school.entity.SchoolClass;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {

    boolean existsByCode(String code);

    boolean existsByLevelId(Long levelId);

    boolean existsBySectionId(Long sectionId);

    boolean existsByRoomId(Long roomId);

    @Query("""
            SELECT c FROM SchoolClass c
            WHERE (:search IS NULL OR :search = '' OR
                   LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(c.code) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:levelId IS NULL OR c.level.id = :levelId)
              AND (:sectionId IS NULL OR c.section.id = :sectionId)
            """)
    Page<SchoolClass> search(@Param("search") String search,
                             @Param("levelId") Long levelId,
                             @Param("sectionId") Long sectionId,
                             Pageable pageable);

    List<SchoolClass> findBySectionId(Long sectionId);

    Optional<SchoolClass> findFirstByNameIgnoreCase(String name);

    Optional<SchoolClass> findFirstByCodeIgnoreCase(String code);

    List<SchoolClass> findByLevelEducationCycle(com.school.enums.EducationCycle cycle);
}
