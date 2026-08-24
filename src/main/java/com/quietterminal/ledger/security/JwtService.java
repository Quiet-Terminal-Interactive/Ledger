package com.quietterminal.ledger.security;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private static final int MIN_KEY_BYTES = 32;

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${ledger.jwt.secret}") String secret,
            @Value("${ledger.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(decodeSecret(secret));
        this.expirationMs = expirationMs;
    }

    public IssuedToken generateToken(UUID userId, String username, String role, Set<String> permissions) {
        UUID sessionId = UUID.randomUUID();
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusMillis(expirationMs);

        String token = Jwts.builder()
                .id(sessionId.toString())
                .subject(username)
                .claim("uid", userId.toString())
                .claim("role", role)
                .claim("permissions", permissions)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();

        return new IssuedToken(token, sessionId, issuedAt, expiresAt);
    }

    public record IssuedToken(String token, UUID sessionId, Instant issuedAt, Instant expiresAt) {
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static byte[] decodeSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is not set. Generate one with `openssl rand -base64 32` and set it as an "
                            + "environment variable before starting the app.");
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET must be base64-encoded.", e);
        }

        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must decode to at least 256 bits (32 bytes). Generate one with "
                            + "`openssl rand -base64 32`.");
        }

        return keyBytes;
    }
}
