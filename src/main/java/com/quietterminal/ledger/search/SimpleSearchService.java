package com.quietterminal.ledger.search;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.quietterminal.ledger.entity.Task;
import com.quietterminal.ledger.entity.Upload;
import com.quietterminal.ledger.entity.WikiPage;
import com.quietterminal.ledger.enums.SearchResultType;
import com.quietterminal.ledger.repository.TaskRepository;
import com.quietterminal.ledger.repository.UploadRepository;
import com.quietterminal.ledger.repository.WikiPageRepository;

@Service
@Profile("dev")
public class SimpleSearchService implements SearchService {

    private static final int SNIPPET_LENGTH = 200;

    private final TaskRepository taskRepository;
    private final WikiPageRepository wikiPageRepository;
    private final UploadRepository uploadRepository;

    public SimpleSearchService(TaskRepository taskRepository, WikiPageRepository wikiPageRepository,
            UploadRepository uploadRepository) {
        this.taskRepository = taskRepository;
        this.wikiPageRepository = wikiPageRepository;
        this.uploadRepository = uploadRepository;
    }

    @Override
    public List<SearchHit> search(String query) {
        Stream<SearchHit> taskResults = taskRepository
                .findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query).stream()
                .map(SimpleSearchService::toHit);
        Stream<SearchHit> wikiResults = wikiPageRepository
                .findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(query, query).stream()
                .map(SimpleSearchService::toHit);
        Stream<SearchHit> uploadResults = uploadRepository.findByFileNameContainingIgnoreCase(query).stream()
                .map(SimpleSearchService::toHit);

        return Stream.of(taskResults, wikiResults, uploadResults).flatMap(s -> s).toList();
    }

    private static SearchHit toHit(Task task) {
        return new SearchHit(SearchResultType.TASK, task.getUUID(), task.getTitle(), snippet(task.getDescription()));
    }

    private static SearchHit toHit(WikiPage page) {
        return new SearchHit(SearchResultType.WIKI_PAGE, page.getUUID(), page.getTitle(),
                snippet(page.getContent()));
    }

    private static SearchHit toHit(Upload upload) {
        return new SearchHit(SearchResultType.UPLOAD, upload.getUUID(), upload.getFileName(), null);
    }

    private static String snippet(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.length() <= SNIPPET_LENGTH ? text : text.substring(0, SNIPPET_LENGTH) + "...";
    }
}
