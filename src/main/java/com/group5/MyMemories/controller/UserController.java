package com.group5.MyMemories.controller;

import com.group5.MyMemories.dto.MemoryDTO;
import com.group5.MyMemories.dto.request.MemoryRequest;
import com.group5.MyMemories.entity.Memory;
import com.group5.MyMemories.entity.User;
import com.group5.MyMemories.exception.ApiException;
import com.group5.MyMemories.repositories.MemoryRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memories")
@RequiredArgsConstructor
public class UserController {

    private final MemoryRepository memoryRepository;

    @GetMapping
    public Page<MemoryDTO> getMemories(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Memory> memories = StringUtils.hasText(q)
                ? memoryRepository.searchByUserId(user.getId(), q.trim(), pageable)
                : memoryRepository.findByUserId(user.getId(), pageable);

        return memories.map(MemoryDTO::from);
    }

    @PostMapping
    public ResponseEntity<MemoryDTO> createMemory(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody(required = false) MemoryRequest request) {
        request = requireRequest(request);
        Memory memory = Memory.builder()
                .title(request.title())
                .content(request.content())
                .user(user)
                .build();

        Memory savedMemory = memoryRepository.save(memory);
        return ResponseEntity.status(HttpStatus.CREATED).body(MemoryDTO.from(savedMemory));
    }

    @GetMapping("/{id}")
    public MemoryDTO getMemory(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return MemoryDTO.from(findOwnedMemory(id, user));
    }

    @PutMapping("/{id}")
    public MemoryDTO updateMemory(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) MemoryRequest request) {
        request = requireRequest(request);
        Memory memory = findOwnedMemory(id, user);
        memory.setTitle(request.title());
        memory.setContent(request.content());
        return MemoryDTO.from(memoryRepository.save(memory));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMemory(@AuthenticationPrincipal User user, @PathVariable Long id) {
        memoryRepository.delete(findOwnedMemory(id, user));
        return ResponseEntity.noContent().build();
    }

    private MemoryRequest requireRequest(MemoryRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        return request;
    }

    private Memory findOwnedMemory(Long id, User user) {
        return memoryRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Memory not found"));
    }
}
