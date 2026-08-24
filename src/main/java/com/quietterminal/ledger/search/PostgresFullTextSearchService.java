package com.quietterminal.ledger.search;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.quietterminal.ledger.enums.SearchResultType;
import com.quietterminal.ledger.repository.TaskRepository;
import com.quietterminal.ledger.repository.TaskRepository.TaskSearchHit;
import com.quietterminal.ledger.repository.UploadRepository;
import com.quietterminal.ledger.repository.UploadRepository.UploadSearchHit;
import com.quietterminal.ledger.repository.WikiPageRepository;
import com.quietterminal.ledger.repository.WikiPageRepository.WikiPageSearchHit;

@Service
@Profile("!dev")
public class PostgresFullTextSearchService implements SearchService {

    private static final int SNIPPET_LENGTH = 200;

    private final TaskRepository taskRepository;
    private final WikiPageRepository wikiPageRepository;
    private final UploadRepository uploadRepository;

    public PostgresFullTextSearchService(TaskRepository taskRepository, WikiPageRepository wikiPageRepository,
            UploadRepository uploadRepository) {
        this.taskRepository = taskRepository;
        this.wikiPageRepository = wikiPageRepository;
        this.uploadRepository = uploadRepository;
    }

    @Override
    public List<SearchHit> search(String query) {
        Stream<SearchHit> taskResults = taskRepository.searchFullText(query).stream()
                .map(PostgresFullTextSearchService::toHit);
        Stream<SearchHit> wikiResults = wikiPageRepository.searchFullText(query).stream()
                .map(PostgresFullTextSearchService::toHit);
        Stream<SearchHit> uploadResults = uploadRepository.searchFullText(query).stream()
                .map(PostgresFullTextSearchService::toHit);

        return Stream.of(taskResults, wikiResults, uploadResults).flatMap(s -> s).toList();
    }

    private static SearchHit toHit(TaskSearchHit task) {
        return new SearchHit(SearchResultType.TASK, task.getId(), task.getTitle(), snippet(task.getDescription()));
    }

    private static SearchHit toHit(WikiPageSearchHit page) {
        return new SearchHit(SearchResultType.WIKI_PAGE, page.getId(), page.getTitle(), snippet(page.getContent()));
    }

    private static SearchHit toHit(UploadSearchHit upload) {
        return new SearchHit(SearchResultType.UPLOAD, upload.getId(), upload.getFileName(), null);
    }

    private static String snippet(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.length() <= SNIPPET_LENGTH ? text : text.substring(0, SNIPPET_LENGTH) + "...";
    }
}
