package com.group5.MyMemories.repositories;

import com.group5.MyMemories.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findAllByUserIdAndNameIn(Long userId, List<String> names);

    @Query("""
            select tag.name as name, count(memory.id) as memoryCount
            from Tag tag left join tag.memories memory
            where tag.user.id = :userId
            group by tag.id, tag.name
            order by tag.name
            """)
    List<TagCountView> findCountsByUserId(@Param("userId") Long userId);

    interface TagCountView {
        String getName();
        long getMemoryCount();
    }
}
