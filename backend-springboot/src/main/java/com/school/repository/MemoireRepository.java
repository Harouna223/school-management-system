package com.school.repository;

import com.school.entity.Memoire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemoireRepository extends JpaRepository<Memoire, Long> {

    List<Memoire> findByStudentIdOrderByDefenseDateDesc(Long studentId);

    List<Memoire> findBySubjectContainingIgnoreCase(String subject);
}