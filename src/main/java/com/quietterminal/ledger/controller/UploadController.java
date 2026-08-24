package com.quietterminal.ledger.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.quietterminal.ledger.entity.Upload;
import com.quietterminal.ledger.entity.User;
import com.quietterminal.ledger.error.LedgerError;
import com.quietterminal.ledger.error.UploadInvalidException;
import com.quietterminal.ledger.error.UploadNotFoundException;
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

    private static final String DIRECTORY_CONTENT_TYPE = "application/x-directory";

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
            @RequestParam("file") MultipartFile file, @RequestParam("bucket") String bucket,
            @RequestParam(name = "path", defaultValue = "") String path) {
        if (file == null || file.isEmpty()) {
            throw new UploadInvalidException("An uploaded file is required.");
        }
        String parentPath = normalizePath(path);
        assertPathExists(bucket, parentPath);
        User uploader = userRepository.findById(principal.userId())
                .orElseThrow(() -> new UserNotFoundException("No user found with id " + principal.userId() + "."));

        String fileName = sanitizeFileName(file.getOriginalFilename());
        String objectKey = UUID.randomUUID() + "-" + fileName;

        try {
            uploadStorage.put(bucket, objectKey, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new UploadStorageException("Could not read uploaded file: " + e.getMessage());
        }

        Upload upload = new Upload(fileName, file.getContentType(), file.getSize(), objectKey, bucket, parentPath,
                uploader);
        uploadRepository.save(upload);
        eventPublisher.publishEvent(new UploadCreatedEvent(upload));
        return ResponseEntity.status(HttpStatus.CREATED).body(toView(upload));
    }

    @GetMapping
    public List<UploadView> listUploads(@RequestParam(name = "bucket", required = false) String bucket,
            @RequestParam(name = "path", defaultValue = "") String path) {
        String parentPath = normalizePath(path);
        if (!parentPath.isEmpty() && (bucket == null || bucket.isBlank())) {
            throw new UploadInvalidException("A bucket is required when browsing into a folder.");
        }
        List<Upload> uploads = parentPath.isEmpty()
                ? uploadRepository.findAllByParentPathOrderByUploadedAtDesc("")
                : uploadRepository.findAllByBucketAndParentPathOrderByUploadedAtDesc(bucket, parentPath);
        return uploads.stream().map(UploadController::toView).toList();
    }

    @PostMapping("/directory")
    public ResponseEntity<UploadView> createDirectory(@AuthenticationPrincipal LedgerPrincipal principal,
            @RequestBody CreateDirectoryRequest request) {
        String name = sanitizeDirectoryName(request.name());
        String parentPath = normalizePath(request.parentPath());
        assertPathExists(request.bucket(), parentPath);
        User creator = userRepository.findById(principal.userId())
                .orElseThrow(() -> new UserNotFoundException("No user found with id " + principal.userId() + "."));

        String objectKey = UUID.randomUUID() + "-" + name + "/";
        uploadStorage.put(request.bucket(), objectKey, new ByteArrayInputStream(new byte[0]), 0, DIRECTORY_CONTENT_TYPE);

        Upload upload = new Upload(name, DIRECTORY_CONTENT_TYPE, 0, objectKey, request.bucket(), parentPath, creator);
        uploadRepository.save(upload);
        return ResponseEntity.status(HttpStatus.CREATED).body(toView(upload));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUpload(@PathVariable("id") UUID id) {
        Upload upload = uploadRepository.findById(id)
                .orElseThrow(() -> new UploadNotFoundException("No upload found with id " + id + "."));
        if (isDirectory(upload)) {
            List<Upload> descendants = uploadRepository.findAllUnderPath(upload.getBucket(), fullPath(upload));
            descendants.forEach(descendant -> uploadStorage.remove(descendant.getBucket(), descendant.getObjectKey()));
            uploadRepository.deleteAll(descendants);
        }
        uploadStorage.remove(upload.getBucket(), upload.getObjectKey());
        uploadRepository.delete(upload);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> downloadContent(@PathVariable("id") UUID id) {
        Upload upload = uploadRepository.findById(id)
                .orElseThrow(() -> new UploadNotFoundException("No upload found with id " + id + "."));

        InputStream data = uploadStorage.get(upload.getBucket(), upload.getObjectKey());
        MediaType contentType = upload.getContentType() != null && !upload.getContentType().isBlank()
                ? MediaType.parseMediaType(upload.getContentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        String disposition = ContentDisposition.attachment()
                .filename(upload.getFileName(), StandardCharsets.UTF_8)
                .build()
                .toString();

        return ResponseEntity.ok()
                .contentType(contentType)
                .header("Content-Disposition", disposition)
                .body(new InputStreamResource(data));
    }

    @ExceptionHandler(LedgerError.class)
    public ResponseEntity<String> handleLedgerError(LedgerError e) {
        HttpStatus status = e instanceof UserNotFoundException || e instanceof UploadNotFoundException
                ? HttpStatus.NOT_FOUND
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

    private static String sanitizeDirectoryName(String name) {
        if (name == null || name.isBlank()) {
            throw new UploadInvalidException("Folder name cannot be blank.");
        }
        String trimmed = name.trim();
        if (trimmed.contains("/") || trimmed.contains("\\")) {
            throw new UploadInvalidException("Folder name cannot contain slashes.");
        }
        return trimmed;
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String trimmed = path.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private void assertPathExists(String bucket, String path) {
        if (path.isEmpty()) {
            return;
        }
        if (bucket == null || bucket.isBlank()) {
            throw new UploadInvalidException("A bucket is required when uploading into a folder.");
        }
        int lastSlash = path.lastIndexOf('/');
        String parent = lastSlash >= 0 ? path.substring(0, lastSlash) : "";
        String name = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        boolean exists = uploadRepository.findAllByBucketAndParentPathOrderByUploadedAtDesc(bucket, parent).stream()
                .anyMatch(u -> isDirectory(u) && u.getFileName().equals(name));
        if (!exists) {
            throw new UploadInvalidException("Folder '" + path + "' does not exist in bucket '" + bucket + "'.");
        }
    }

    private static boolean isDirectory(Upload upload) {
        return DIRECTORY_CONTENT_TYPE.equals(upload.getContentType());
    }

    private static String fullPath(Upload upload) {
        return upload.getParentPath().isEmpty() ? upload.getFileName()
                : upload.getParentPath() + "/" + upload.getFileName();
    }

    private static UploadView toView(Upload upload) {
        return new UploadView(upload.getUUID(), upload.getFileName(), upload.getContentType(),
                upload.getSizeBytes(), upload.getBucket(), upload.getParentPath(), isDirectory(upload),
                upload.getUploadedBy().getUUID(), upload.getUploadedAt());
    }

    public record UploadView(UUID id, String fileName, String contentType, long sizeBytes, String bucket,
            String parentPath, boolean directory, UUID uploadedBy, Instant uploadedAt) {
    }

    public record CreateDirectoryRequest(String bucket, String name, String parentPath) {
    }
}
