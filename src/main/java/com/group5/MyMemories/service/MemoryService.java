package com.group5.MyMemories.service;

import com.group5.MyMemories.dto.MemoryDTO;
import com.group5.MyMemories.dto.request.MemoryRequest;
import com.group5.MyMemories.entity.Memory;
import com.group5.MyMemories.entity.Tag;
import com.group5.MyMemories.entity.User;
import com.group5.MyMemories.exception.ApiException;
import com.group5.MyMemories.repositories.MemoryRepository;
import com.group5.MyMemories.repositories.TagRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class MemoryService {

    private final MemoryRepository memoryRepository;
    private final TagRepository tagRepository;

    @Transactional(readOnly = true)
    public Page<MemoryDTO> getMemories(
            Long userId,
            String query,
            LocalDate from,
            LocalDate to,
            List<String> requestedTags,
            Pageable pageable) {
        List<String> tagNames = requestedTags == null ? List.of() : requestedTags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        Pageable cappedPageable = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), 50),
                pageable.getSort());
        Page<Memory> memories = memoryRepository.findAll(
                memoryFilters(userId, query, from, to, tagNames), cappedPageable);
        return memories.map(MemoryDTO::from);
    }

    public MemoryDTO createMemory(User user, MemoryRequest request) {
        MemoryRequest requiredRequest = requireRequest(request);
        Memory memory = Memory.builder()
                .title(requiredRequest.title())
                .content(requiredRequest.content())
                .memoryDate(memoryDate(requiredRequest))
                .user(user)
                .tags(resolveTags(user, requiredRequest.tags()))
                .build();
        return MemoryDTO.from(memoryRepository.save(memory));
    }

    @Transactional(readOnly = true)
    public MemoryDTO getMemory(Long userId, Long id) {
        return MemoryDTO.from(findOwnedMemory(id, userId));
    }

    public MemoryDTO updateMemory(Long userId, Long id, MemoryRequest request) {
        MemoryRequest requiredRequest = requireRequest(request);
        Memory memory = findOwnedMemory(id, userId);
        memory.setTitle(requiredRequest.title());
        memory.setContent(requiredRequest.content());
        memory.setMemoryDate(memoryDate(requiredRequest));
        Set<Tag> tags = resolveTags(memory.getUser(), requiredRequest.tags());
        memory.getTags().clear();
        memory.getTags().addAll(tags);
        return MemoryDTO.from(memoryRepository.save(memory));
    }

    public void deleteMemory(Long userId, Long id) {
        memoryRepository.delete(findOwnedMemory(id, userId));
    }

    private MemoryRequest requireRequest(MemoryRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        return request;
    }

    private LocalDate memoryDate(MemoryRequest request) {
        return request.memoryDate() == null ? LocalDate.now() : request.memoryDate();
    }

    private Set<Tag> resolveTags(User user, List<String> requestedTags) {
        if (requestedTags == null || requestedTags.isEmpty()) {
            return new HashSet<>();
        }

        Set<String> normalizedNames = new TreeSet<>();
        for (String requestedTag : requestedTags) {
            if (requestedTag == null || requestedTag.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Tag name is required", "tags");
            }

            String normalizedName = requestedTag.trim().toLowerCase(Locale.ROOT);
            if (normalizedName.length() > 30) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Tag name must be at most 30 characters", "tags");
            }
            normalizedNames.add(normalizedName);
        }

        if (normalizedNames.size() > 10) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A memory can have at most 10 tags", "tags");
        }

        List<Tag> existingTags = tagRepository.findAllByUserIdAndNameIn(user.getId(), List.copyOf(normalizedNames));
        Set<String> existingNames = existingTags.stream().map(Tag::getName).collect(Collectors.toSet());
        List<Tag> newTags = normalizedNames.stream()
                .filter(name -> !existingNames.contains(name))
                .map(name -> new Tag(name, user))
                .toList();

        if (!newTags.isEmpty()) {
            existingTags.addAll(tagRepository.saveAll(newTags));
        }
        return new HashSet<>(existingTags);
    }

    private Specification<Memory> memoryFilters(
            Long userId,
            String query,
            LocalDate from,
            LocalDate to,
            List<String> tagNames) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("user").get("id"), userId));

            if (query != null && !query.isBlank()) {
                String searchText = query.trim().toLowerCase(Locale.ROOT);
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.gt(
                                criteriaBuilder.locate(criteriaBuilder.lower(root.get("title")), searchText), 0),
                        criteriaBuilder.gt(
                                criteriaBuilder.locate(
                                        criteriaBuilder.lower(
                                                criteriaBuilder.coalesce(root.get("content"), "")),
                                        searchText),
                                0)));
            }

            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("memoryDate"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("memoryDate"), to));
            }
            if (!tagNames.isEmpty()) {
                Subquery<Long> tagSubquery = criteriaQuery.subquery(Long.class);
                Root<Memory> correlatedMemory = tagSubquery.correlate(root);
                Join<Memory, Tag> tagJoin = correlatedMemory.join("tags");
                tagSubquery.select(tagJoin.get("id"))
                        .where(
                                tagJoin.get("name").in(tagNames),
                                criteriaBuilder.equal(tagJoin.get("user").get("id"), userId));
                predicates.add(criteriaBuilder.exists(tagSubquery));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Memory findOwnedMemory(Long id, Long userId) {
        return memoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Memory not found"));
    }
}
