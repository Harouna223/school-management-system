package com.school.repository;

import com.school.entity.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettingRepository extends JpaRepository<Setting, String> {

    Optional<Setting> findByKey(String key);
}