package com.school.repository;

import com.school.entity.FeeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeeTypeRepository extends JpaRepository<FeeType, Long> {
    Optional<FeeType> findByName(String name);
}
