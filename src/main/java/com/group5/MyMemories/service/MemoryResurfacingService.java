package com.group5.MyMemories.service;

import com.group5.MyMemories.dto.MemoryDTO;
import com.group5.MyMemories.dto.response.OnThisDayYearGroup;
import com.group5.MyMemories.entity.Memory;
import com.group5.MyMemories.entity.ResurfacingHistory;
import com.group5.MyMemories.entity.ResurfaceFrequency;
import com.group5.MyMemories.entity.User;
import com.group5.MyMemories.repositories.MemoryRepository;
import com.group5.MyMemories.repositories.ResurfacingHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Transactional
public class MemoryResurfacingService {

    private final MemoryRepository memoryRepository;
    private final ResurfacingHistoryRepository historyRepository;

    @Transactional(readOnly = true)
    public List<OnThisDayYearGroup> getOnThisDay(Long userId, LocalDate date) {
        boolean includeLeapDay = date.getMonthValue() == 2 && date.getDayOfMonth() == 28 && !date.isLeapYear();
        List<Memory> memories = memoryRepository.findOnThisDay(
                userId,
                date.getMonthValue(),
                date.getDayOfMonth(),
                date.getYear(),
                includeLeapDay);
        TreeMap<Integer, List<MemoryDTO>> groups = new TreeMap<>(Comparator.reverseOrder());
        for (Memory memory : memories) {
            groups.computeIfAbsent(memory.getMemoryDate().getYear(), year -> new ArrayList<>())
                    .add(MemoryDTO.from(memory));
        }
        return groups.entrySet().stream()
                .map(entry -> new OnThisDayYearGroup(entry.getKey(), entry.getValue()))
                .toList();
    }

    public Optional<MemoryDTO> resurface(User user, LocalDate date) {
        Long userId = user.getId();
        if (user.getResurfaceFrequency() == ResurfaceFrequency.OFF) {
            return Optional.empty();
        }

        Optional<ResurfacingHistory> existingToday =
                historyRepository.findByUserIdAndSurfacedDate(userId, date);
        if (existingToday.isPresent()) {
            return Optional.of(MemoryDTO.from(existingToday.get().getMemory()));
        }

        if (user.getResurfaceFrequency() == ResurfaceFrequency.WEEKLY) {
            Optional<ResurfacingHistory> mostRecent = historyRepository
                    .findTopByUserIdAndSurfacedDateBeforeOrderBySurfacedDateDesc(userId, date);
            if (mostRecent.isPresent()
                    && ChronoUnit.DAYS.between(mostRecent.get().getSurfacedDate(), date) < 7) {
                return Optional.empty();
            }
        }

        Long previousMemoryId = historyRepository.findByUserIdAndSurfacedDate(userId, date.minusDays(1))
                .map(history -> history.getMemory().getId())
                .orElse(null);

        List<Memory> onThisDay = memoryRepository.findOnThisDay(
                userId,
                date.getMonthValue(),
                date.getDayOfMonth(),
                date.getYear(),
                date.getMonthValue() == 2 && date.getDayOfMonth() == 28 && !date.isLeapYear());
        if (previousMemoryId != null) {
            List<Memory> alternatives = onThisDay.stream()
                    .filter(memory -> !memory.getId().equals(previousMemoryId))
                    .toList();
            if (!alternatives.isEmpty()) {
                onThisDay = alternatives;
            } else if (!onThisDay.isEmpty()) {
                Memory alternative = findRandomFallback(userId, date.minusYears(1), previousMemoryId).orElse(null);
                if (alternative != null) {
                    onThisDay = List.of(alternative);
                }
            }
        }

        Memory selectedMemory;
        if (!onThisDay.isEmpty()) {
            selectedMemory = onThisDay.get(ThreadLocalRandom.current().nextInt(onThisDay.size()));
        } else {
            selectedMemory = findRandomFallback(userId, date.minusYears(1), previousMemoryId).orElse(null);
        }

        if (selectedMemory == null) {
            return Optional.empty();
        }

        historyRepository.save(new ResurfacingHistory(user, selectedMemory, date));
        return Optional.of(MemoryDTO.from(selectedMemory));
    }

    private Optional<Memory> findRandomFallback(Long userId, LocalDate cutoffDate, Long previousMemoryId) {
        long count = previousMemoryId == null
                ? memoryRepository.countByUserIdAndMemoryDateLessThanEqual(userId, cutoffDate)
                : memoryRepository.countByUserIdAndMemoryDateLessThanEqualAndIdNot(userId, cutoffDate, previousMemoryId);

        if (count == 0 && previousMemoryId != null) {
            return findRandomFallback(userId, cutoffDate, null);
        }
        if (count == 0) {
            return Optional.empty();
        }

        int offset = ThreadLocalRandom.current().nextInt(Math.toIntExact(count));
        List<Memory> results = previousMemoryId == null
                ? memoryRepository.findByUserIdAndMemoryDateLessThanEqualOrderById(
                        userId, cutoffDate, PageRequest.of(offset, 1))
                : memoryRepository.findByUserIdAndMemoryDateLessThanEqualAndIdNotOrderById(
                        userId, cutoffDate, previousMemoryId, PageRequest.of(offset, 1));
        return results.stream().findFirst();
    }
}
