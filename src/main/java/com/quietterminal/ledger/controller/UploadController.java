package com.quietterminal.ledger.controller;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.quietterminal.ledger.entity.Upload;
import com.quietterminal.ledger.entity.User;
import com.quietterminal.ledger.error.LedgerError;
import com.quietterminal.ledger.error.UploadInvalidException;
import com.quietterminal.ledger.error.UploadStorageException;
import com.quietterminal.ledger.error.UserNotFoundException;
import com.quietterminal.ledger.event.UploadCreatedEvent;
import com.quietterminal.ledger.repository.UploadRepository;
import com.quietterminal.ledger.repository.UserRepository;
import com.quietterminal.ledger.security.LedgerPrincipal;
import com.quietterminal.ledger.storage.MinioUploadStorage;

@RestController
@RequestMapping("/uploads")
public class UploadController {

    private final UploadRepository uploadRepository;
    private final UserRepository userRepository;
    private final MinioUploadStorage uploadStorage;
    private final ApplicationEventPublisher eventPublisher;

    public UploadController(UploadRepository uploadRepository, UserRepository userRepository,
            MinioUploadStorage uploadStorage, ApplicationEventPublisher eventPublisher) {
        this.uploadRepository = uploadRepository;
        this.userRepository = userRepository;
        this.uploadStorage = uploadStorage;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping
    public ResponseEntity<UploadView> upload(@AuthenticationPrincipal LedgerPrincipal principal,
            @RequestParam("file") MultipartFile file, @RequestParam("bucket") String bucket) {
        if (file == null || file.isEmpty()) {
            throw new UploadInvalidException("An uploaded file is required.");
        }
        User uploader = userRepository.findById(principal.userId())
                .orElseThrow(() -> new UserNotFoundException("No user found with id " + principal.userId() + "."));

        String fileName = sanitizeFileName(file.getOriginalFilename());
        String objectKey = UUID.randomUUID() + "-" + fileName;

        try {
            uploadStorage.put(bucket, objectKey, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new UploadStorageException("Could not read uploaded file: " + e.getMessage());
        }

        Upload upload = new Upload(fileName, file.getContentType(), file.getSize(), objectKey, bucket, uploader);
        uploadRepository.save(upload);
        eventPublisher.publishEvent(new UploadCreatedEvent(upload));
        return ResponseEntity.status(HttpStatus.CREATED).body(toView(upload));
    }

    @GetMapping
    public List<UploadView> listUploads() {
        return uploadRepository.findAllByOrderByUploadedAtDesc().stream().map(UploadController::toView).toList();
    }

    @ExceptionHandler(LedgerError.class)
    public ResponseEntity<String> handleLedgerError(LedgerError e) {
        HttpStatus status = e instanceof UserNotFoundException ? HttpStatus.NOT_FOUND
                : e instanceof UploadStorageException ? HttpStatus.BAD_GATEWAY
                        : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(e.getMessage());
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "upload";
        }
        String normalized = name.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    }

    private static UploadView toView(Upload upload) {
        return new UploadView(upload.getUUID(), upload.getFileName(), upload.getContentType(),
                upload.getSizeBytes(), upload.getBucket(), upload.getUploadedBy().getUUID(), upload.getUploadedAt());
    }

    public record UploadView(UUID id, String fileName, String contentType, long sizeBytes, String bucket,
            UUID uploadedBy, Instant uploadedAt) {
    }
}
