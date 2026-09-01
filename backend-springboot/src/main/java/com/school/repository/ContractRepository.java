package com.school.repository;

import com.school.entity.Contract;
import com.school.enums.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    List<Contract> findByTeacherId(Long teacherId);

    List<Contract> findByStatus(ContractStatus status);

    List<Contract> findByTeacherIdOrderByStartDateDesc(Long teacherId);

    @Query("SELECT c FROM Contract c WHERE (:teacherId IS NULL OR c.teacher.id = :teacherId) AND (:status IS NULL OR c.status = :status) ORDER BY c.startDate DESC")
    List<Contract> search(@Param("teacherId") Long teacherId, @Param("status") ContractStatus status);
}