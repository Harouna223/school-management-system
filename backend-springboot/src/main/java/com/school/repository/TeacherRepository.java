package com.school.repository;

import com.school.entity.Teacher;
import java.util.Optional;
import com.school.enums.TeacherStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    boolean existsByEmployeeNo(String employeeNo);

    long countByStatus(TeacherStatus status);

    Optional<Teacher> findByUserId(Long userId);

    @Query("""
            SELECT t FROM Teacher t
            WHERE (:search IS NULL OR :search = '' OR
                   LOWER(t.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(t.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(t.employeeNo) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:status IS NULL OR t.status = :status)
            """)
    Page<Teacher> search(@Param("search") String search,
                         @Param("status") TeacherStatus status,
                         Pageable pageable);
}
