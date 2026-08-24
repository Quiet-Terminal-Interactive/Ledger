package com.quietterminal.ledger.storage;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.quietterminal.ledger.error.UploadInvalidException;
import com.quietterminal.ledger.error.UploadStorageException;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;

@Component
public class MinioUploadStorage {

    private final MinioClient minioClient;
    private final Set<String> buckets;

    public MinioUploadStorage(MinioClient minioClient, @Value("${ledger.minio.buckets:}") String bucketsRaw) {
        this.minioClient = minioClient;
        this.buckets = parseBuckets(bucketsRaw);
    }

    public void put(String bucket, String objectKey, InputStream data, long size, String contentType) {
        if (bucket == null || !buckets.contains(bucket)) {
            throw new UploadInvalidException("'" + bucket + "' is not a configured upload bucket.");
        }
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(data, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new UploadStorageException("Failed to store upload in MinIO: " + e.getMessage());
        }
    }

    public InputStream get(String bucket, String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new UploadStorageException("Failed to retrieve upload from MinIO: " + e.getMessage());
        }
    }

    public void remove(String bucket, String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new UploadStorageException("Failed to delete upload from MinIO: " + e.getMessage());
        }
    }

    private static Set<String> parseBuckets(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
