package com.school.repository;

import com.school.entity.Alumnus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlumnusRepository extends JpaRepository<Alumnus, Long> {

    List<Alumnus> findByFieldId(Long fieldId);

    List<Alumnus> findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCase(String lastName, String firstName);
}