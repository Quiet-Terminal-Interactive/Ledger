package com.quietterminal.ledger.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.quietterminal.ledger.entity.WikiPage;

public interface WikiPageRepository extends JpaRepository<WikiPage, UUID> {

    Optional<WikiPage> findByPath(String path);

    List<WikiPage> findAllByOrderByPathAsc();

    List<WikiPage> findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(String titleFragment,
            String contentFragment);

    @Query(value = """
            SELECT id, title, content FROM wiki_pages
            WHERE to_tsvector('english', coalesce(title, '') || ' ' || coalesce(content, ''))
                  @@ plainto_tsquery('english', :query)
            ORDER BY ts_rank(to_tsvector('english', coalesce(title, '') || ' ' || coalesce(content, '')),
                              plainto_tsquery('english', :query)) DESC
            """, nativeQuery = true)
    List<WikiPageSearchHit> searchFullText(@Param("query") String query);

    interface WikiPageSearchHit {
        UUID getId();

        String getTitle();

        String getContent();
    }
}
