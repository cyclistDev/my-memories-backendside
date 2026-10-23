package com.group5.MyMemories.controller;

import com.group5.MyMemories.dto.response.TagResponse;
import com.group5.MyMemories.entity.User;
import com.group5.MyMemories.repositories.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagRepository tagRepository;

    @GetMapping
    public List<TagResponse> getTags(@AuthenticationPrincipal User user) {
        return tagRepository.findCountsByUserId(user.getId()).stream()
                .map(tag -> new TagResponse(tag.getName(), tag.getMemoryCount()))
                .toList();
    }
}
