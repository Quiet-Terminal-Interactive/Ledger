package com.quietterminal.ledger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.quietterminal.ledger.error.UploadInvalidException;
import com.quietterminal.ledger.storage.MinioUploadStorage;

import io.minio.MinioClient;

class MinioUploadStorageTest {

    @Test
    void puttingToAnUnconfiguredBucketIsRejectedWithoutTouchingMinio() {
        MinioClient minioClient = mock(MinioClient.class);
        MinioUploadStorage storage = new MinioUploadStorage(minioClient, "ledger-uploads,ledger-attachments");
        InputStream data = new ByteArrayInputStream("hello".getBytes());

        assertThrows(UploadInvalidException.class,
                () -> storage.put("not-a-real-bucket", "key", data, 5, "text/plain"));

        verifyNoInteractions(minioClient);
    }

    @Test
    void puttingWithNoConfiguredBucketsIsRejected() {
        MinioClient minioClient = mock(MinioClient.class);
        MinioUploadStorage storage = new MinioUploadStorage(minioClient, "");
        InputStream data = new ByteArrayInputStream("hello".getBytes());

        assertThrows(UploadInvalidException.class,
                () -> storage.put("ledger-uploads", "key", data, 5, "text/plain"));

        verifyNoInteractions(minioClient);
    }
}
