package com.school.repository;

import com.school.entity.NumberSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NumberSequenceRepository extends JpaRepository<NumberSequence, String> {

    /**
     * Lit le compteur en le VERROUILLANT : deux requêtes simultanées sont
     * sérialisées, donc deux numéros ne peuvent pas être identiques.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM NumberSequence s WHERE s.prefix = :prefix")
    Optional<NumberSequence> findWithLockingByPrefix(@Param("prefix") String prefix);
}
