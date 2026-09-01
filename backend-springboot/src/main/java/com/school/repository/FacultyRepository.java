package com.school.repository;

import com.school.entity.Faculty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository des facultés.
 */
@Repository
public interface FacultyRepository extends JpaRepository<Faculty, Long> {

    boolean existsByName(String name);

    boolean existsByCode(String code);

    List<Faculty> findTop8ByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(String name, String code);
}
