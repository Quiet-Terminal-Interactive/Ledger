package com.quietterminal.ledger.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.quietterminal.ledger.entity.Upload;

public interface UploadRepository extends JpaRepository<Upload, UUID> {

    List<Upload> findAllByOrderByUploadedAtDesc();

    List<Upload> findByFileNameContainingIgnoreCase(String fileNameFragment);

    @Query(value = """
            SELECT id, file_name AS "fileName" FROM uploads
            WHERE to_tsvector('english', regexp_replace(coalesce(file_name, ''), '[^[:alnum:]]+', ' ', 'g'))
                  @@ plainto_tsquery('english', :query)
            ORDER BY ts_rank(to_tsvector('english', regexp_replace(coalesce(file_name, ''), '[^[:alnum:]]+', ' ', 'g')),
                              plainto_tsquery('english', :query)) DESC
            """, nativeQuery = true)
    List<UploadSearchHit> searchFullText(@Param("query") String query);

    interface UploadSearchHit {
        UUID getId();

        String getFileName();
    }
}
