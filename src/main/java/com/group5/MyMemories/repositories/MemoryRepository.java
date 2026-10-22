package com.group5.MyMemories.repositories;

import com.group5.MyMemories.entity.Memory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemoryRepository extends JpaRepository<Memory, Long> {

    Optional<Memory> findByIdAndUserId(Long id, Long userId);

    Page<Memory> findByUserId(Long userId, Pageable pageable);

    @Query("""
            select memory from Memory memory
            where memory.user.id = :userId
                and (
                    locate(lower(:query), lower(memory.title)) > 0
                    or locate(lower(:query), lower(coalesce(memory.content, ''))) > 0
                )
            """)
    Page<Memory> searchByUserId(
            @Param("userId") Long userId,
            @Param("query") String query,
            Pageable pageable);
}
