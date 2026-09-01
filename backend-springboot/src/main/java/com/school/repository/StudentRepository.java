package com.school.repository;

import com.school.entity.Student;
import com.school.enums.EducationCycle;
import com.school.enums.StudentStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StudentRepository extends JpaRepository<Student, Long> {

    boolean existsByMatricule(String matricule);

    boolean existsByEmail(String email);

    boolean existsByFirstNameAndLastNameAndBirthDate(String firstName, String lastName,
                                                     java.time.LocalDate birthDate);

    long countByStatus(com.school.enums.StudentStatus status);

    long countByStatusAndEducationCycle(com.school.enums.StudentStatus status,
                                        com.school.enums.EducationCycle cycle);

    long countByEducationCycle(com.school.enums.EducationCycle cycle);

    long countBySchoolClassId(Long classId);

    @Query("""
            SELECT s.gender, COUNT(s) FROM Student s
            WHERE (:cycle IS NULL OR s.educationCycle = :cycle)
            GROUP BY s.gender
            """)
    List<Object[]> countByGender(@Param("cycle") com.school.enums.EducationCycle cycle);

    @Query("""
            SELECT s FROM Student s
            WHERE (:search IS NULL OR :search = '' OR
                   LOWER(s.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(s.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(s.matricule) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:classId IS NULL OR s.schoolClass.id = :classId)
              AND (:status IS NULL OR s.status = :status)
              AND (:cycle IS NULL OR s.educationCycle = :cycle)
            """)
    Page<Student> search(@Param("search") String search,
                         @Param("classId") Long classId,
                         @Param("status") com.school.enums.StudentStatus status,
                         @Param("cycle") com.school.enums.EducationCycle cycle,
                         Pageable pageable);

    List<Student> findByParentId(Long parentId);

    List<Student> findBySchoolClassId(Long classId);

    Optional<Student> findByUserId(Long userId);

    @Query("SELECT s.gender, COUNT(s) FROM Student s GROUP BY s.gender")
    List<Object[]> countByGender();
}
