package com.quietterminal.ledger.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.quietterminal.ledger.error.UploadInvalidException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "uploads")
public class Upload {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID uuid;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "object_key", nullable = false, updatable = false, unique = true)
    private String objectKey;

    @Column(name = "bucket", nullable = false, updatable = false)
    private String bucket;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "uploaded_by", nullable = false, updatable = false)
    private User uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    protected Upload() {
    }

    public Upload(String fileName, String contentType, long sizeBytes, String objectKey, String bucket,
            User uploadedBy) {
        Objects.requireNonNull(objectKey, "Object key cannot be null.");
        Objects.requireNonNull(bucket, "Bucket cannot be null.");
        Objects.requireNonNull(uploadedBy, "Uploader cannot be null.");
        if (fileName == null || fileName.isBlank()) {
            throw new UploadInvalidException("File name cannot be blank.");
        }

        this.uuid = UUID.randomUUID();
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.objectKey = objectKey;
        this.bucket = bucket;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = Instant.now();
    }

    public UUID getUUID() {
        return uuid;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getBucket() {
        return bucket;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Upload other)) {
            return false;
        }
        return uuid != null && uuid.equals(other.uuid);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
