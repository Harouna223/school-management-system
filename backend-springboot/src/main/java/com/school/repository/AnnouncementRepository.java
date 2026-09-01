package com.school.repository;

import com.school.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findAllByOrderByPinnedDescCreatedAtDesc();

    List<Announcement> findByTargetRoleIsNullOrderByPinnedDescCreatedAtDesc();

    List<Announcement> findByTargetRoleOrTargetRoleIsNullOrderByPinnedDescCreatedAtDesc(String role);
}