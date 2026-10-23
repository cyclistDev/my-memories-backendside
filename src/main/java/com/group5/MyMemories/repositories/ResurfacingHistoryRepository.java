package com.group5.MyMemories.repositories;

import com.group5.MyMemories.entity.ResurfacingHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ResurfacingHistoryRepository extends JpaRepository<ResurfacingHistory, Long> {

    Optional<ResurfacingHistory> findByUserIdAndSurfacedDate(Long userId, LocalDate surfacedDate);

    Optional<ResurfacingHistory> findTopByUserIdAndSurfacedDateBeforeOrderBySurfacedDateDesc(
            Long userId, LocalDate surfacedDate);
}
