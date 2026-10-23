package com.group5.MyMemories.controller;

import com.group5.MyMemories.dto.MemoryDTO;
import com.group5.MyMemories.dto.request.MemoryRequest;
import com.group5.MyMemories.dto.response.OnThisDayYearGroup;
import com.group5.MyMemories.entity.User;
import com.group5.MyMemories.service.MemoryService;
import com.group5.MyMemories.service.MemoryResurfacingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/memories")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;
    private final MemoryResurfacingService resurfacingService;

    @GetMapping
    public Page<MemoryDTO> getMemories(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) List<String> tag,
            @PageableDefault(size = 20, sort = {"memoryDate", "createdAt"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return memoryService.getMemories(user.getId(), q, from, to, tag, pageable);
    }

    @GetMapping("/on-this-day")
    public List<OnThisDayYearGroup> getOnThisDay(
            @AuthenticationPrincipal User user,
            @RequestParam LocalDate date) {
        return resurfacingService.getOnThisDay(user.getId(), date);
    }

    @GetMapping("/resurface")
    public ResponseEntity<MemoryDTO> resurface(
            @AuthenticationPrincipal User user,
            @RequestParam LocalDate date) {
        Optional<MemoryDTO> memory = resurfacingService.resurface(user, date);
        return memory.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    public ResponseEntity<MemoryDTO> createMemory(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody(required = false) MemoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(memoryService.createMemory(user, request));
    }

    @GetMapping("/{id}")
    public MemoryDTO getMemory(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return memoryService.getMemory(user.getId(), id);
    }

    @PutMapping("/{id}")
    public MemoryDTO updateMemory(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) MemoryRequest request) {
        return memoryService.updateMemory(user.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMemory(@AuthenticationPrincipal User user, @PathVariable Long id) {
        memoryService.deleteMemory(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
