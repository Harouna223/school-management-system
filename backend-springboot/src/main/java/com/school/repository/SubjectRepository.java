package com.school.repository;

import com.school.entity.Subject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    boolean existsByCode(String code);

    @Query("""
            SELECT s FROM Subject s
            WHERE (:search IS NULL OR :search = '' OR
                   LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(s.code) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Subject> search(@Param("search") String search, Pageable pageable);
}
