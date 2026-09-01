package com.school.repository;

import com.school.entity.Parent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParentRepository extends JpaRepository<Parent, Long> {

    Optional<Parent> findByUserId(Long userId);

    List<Parent> findByUserIdOrderByIdAsc(Long userId);

    Optional<Parent> findByPhone(String phone);

    Optional<Parent> findByEmail(String email);
}
