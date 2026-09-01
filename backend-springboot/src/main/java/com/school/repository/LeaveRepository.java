package com.school.repository;

import com.school.entity.Leave;
import com.school.enums.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LeaveRepository extends JpaRepository<Leave, Long> {

    List<Leave> findByTeacherId(Long teacherId);

    List<Leave> findByStatus(LeaveStatus status);

    @Query("SELECT l FROM Leave l WHERE (:teacherId IS NULL OR l.teacher.id = :teacherId) AND (:status IS NULL OR l.status = :status)")
    List<Leave> search(@Param("teacherId") Long teacherId, @Param("status") LeaveStatus status);
}