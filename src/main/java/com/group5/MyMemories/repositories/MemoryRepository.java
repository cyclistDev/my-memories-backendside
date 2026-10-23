package com.group5.MyMemories.repositories;

import com.group5.MyMemories.entity.Memory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MemoryRepository extends JpaRepository<Memory, Long>, JpaSpecificationExecutor<Memory> {

    Optional<Memory> findByIdAndUserId(Long id, Long userId);

    @Query(value = """
            SELECT *
            FROM memories memory
            WHERE memory.user_id = :userId
              AND EXTRACT(MONTH FROM memory.memory_date) = :month
              AND (
                    EXTRACT(DAY FROM memory.memory_date) = :day
                    OR (:includeLeapDay = TRUE
                        AND EXTRACT(MONTH FROM memory.memory_date) = 2
                        AND EXTRACT(DAY FROM memory.memory_date) = 29)
                  )
              AND EXTRACT(YEAR FROM memory.memory_date) < :year
            ORDER BY memory.memory_date DESC, memory.created_at DESC
            """, nativeQuery = true)
    List<Memory> findOnThisDay(
            @Param("userId") Long userId,
            @Param("month") int month,
            @Param("day") int day,
            @Param("year") int year,
            @Param("includeLeapDay") boolean includeLeapDay);

    long countByUserIdAndMemoryDateLessThanEqual(Long userId, LocalDate memoryDate);

    long countByUserIdAndMemoryDateLessThanEqualAndIdNot(
            Long userId, LocalDate memoryDate, Long excludedMemoryId);

    List<Memory> findByUserIdAndMemoryDateLessThanEqualOrderById(
            Long userId, LocalDate memoryDate, org.springframework.data.domain.Pageable pageable);

    List<Memory> findByUserIdAndMemoryDateLessThanEqualAndIdNotOrderById(
            Long userId,
            LocalDate memoryDate,
            Long excludedMemoryId,
            org.springframework.data.domain.Pageable pageable);
}
