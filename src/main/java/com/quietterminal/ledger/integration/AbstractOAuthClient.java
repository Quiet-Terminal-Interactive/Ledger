package com.quietterminal.ledger.integration;

import java.net.URI;
import java.time.Instant;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import tools.jackson.databind.JsonNode;

public abstract class AbstractOAuthClient {

    private final RestTemplate restTemplate;
    private final URI tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;

    private volatile String accessToken;
    private volatile Instant accessTokenExpiry = Instant.EPOCH;

    protected AbstractOAuthClient(RestTemplate restTemplate, URI tokenUri, String clientId, String clientSecret,
            String refreshToken, String configKeyDescription) {
        if (isBlank(clientId) || isBlank(clientSecret) || isBlank(refreshToken)) {
            throw new IllegalStateException(
                    configKeyDescription + " must all be set when this integration is enabled.");
        }
        this.restTemplate = restTemplate;
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
    }

    protected synchronized String ensureAccessToken() {
        if (accessToken != null && Instant.now().isBefore(accessTokenExpiry)) {
            return accessToken;
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", refreshToken);
        body.add("grant_type", "refresh_token");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        JsonNode response;
        try {
            response = restTemplate.exchange(tokenUri, HttpMethod.POST, new HttpEntity<>(body, headers),
                    JsonNode.class).getBody();
        } catch (RestClientException e) {
            throw tokenRefreshFailed("Failed to refresh access token: " + e.getMessage());
        }
        if (response == null || response.path("access_token").asString(null) == null) {
            throw tokenRefreshFailed("Token refresh returned no access token.");
        }

        accessToken = response.path("access_token").asString();
        accessTokenExpiry = Instant.now().plusSeconds(Math.max(0, response.path("expires_in").asLong(3600) - 60));
        return accessToken;
    }

    protected abstract RuntimeException tokenRefreshFailed(String message);

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
