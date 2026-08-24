package com.quietterminal.ledger.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.minio.MinioClient;

@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(@Value("${ledger.minio.endpoint}") String endpoint,
            @Value("${ledger.minio.access-key}") String accessKey,
            @Value("${ledger.minio.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
