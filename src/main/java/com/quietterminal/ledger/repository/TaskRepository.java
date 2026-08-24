package com.quietterminal.ledger.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.quietterminal.ledger.entity.Task;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByAssignees_Uuid(UUID userId);

    List<Task> findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String titleFragment,
            String descriptionFragment);

    @Query(value = """
            SELECT id, title, description FROM tasks
            WHERE to_tsvector('english', coalesce(title, '') || ' ' || coalesce(description, ''))
                  @@ plainto_tsquery('english', :query)
            ORDER BY ts_rank(to_tsvector('english', coalesce(title, '') || ' ' || coalesce(description, '')),
                              plainto_tsquery('english', :query)) DESC
            """, nativeQuery = true)
    List<TaskSearchHit> searchFullText(@Param("query") String query);

    interface TaskSearchHit {
        UUID getId();

        String getTitle();

        String getDescription();
    }
}
