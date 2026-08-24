package com.quietterminal.ledger.gmail;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import com.quietterminal.ledger.error.GmailMessageNotFoundException;
import com.quietterminal.ledger.error.GmailRequestInvalidException;
import com.quietterminal.ledger.error.GmailUpstreamException;
import com.quietterminal.ledger.integration.AbstractOAuthClient;
import com.quietterminal.ledger.integration.Integration;
import com.quietterminal.ledger.integration.IntegrationKind;

import tools.jackson.databind.JsonNode;

@Service
@ConditionalOnProperty(name = "ledger.gmail.enabled", havingValue = "true")
public class GmailService extends AbstractOAuthClient implements Integration {

    private static final URI TOKEN_URI = URI.create("https://oauth2.googleapis.com/token");
    private static final String API_BASE = "https://gmail.googleapis.com/gmail/v1/users/me";
    private static final int MAX_MAX_RESULTS = 50;

    private final RestTemplate restTemplate;

    public GmailService(RestTemplate restTemplate,
            @Value("${ledger.gmail.client-id:}") String clientId,
            @Value("${ledger.gmail.client-secret:}") String clientSecret,
            @Value("${ledger.gmail.refresh-token:}") String refreshToken) {
        super(restTemplate, TOKEN_URI, clientId, clientSecret, refreshToken,
                "ledger.gmail.client-id, client-secret and refresh-token (env LEDGER_GMAIL_CLIENT_ID, "
                        + "LEDGER_GMAIL_CLIENT_SECRET, LEDGER_GMAIL_REFRESH_TOKEN)");
        this.restTemplate = restTemplate;
    }

    @Override
    protected RuntimeException tokenRefreshFailed(String message) {
        return new GmailUpstreamException(message);
    }

    @Override
    public String id() {
        return "gmail";
    }

    @Override
    public String displayName() {
        return "Gmail";
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.OAUTH_APP;
    }

    public MessageListPage listMessages(int maxResults, String pageToken) {
        if (maxResults < 1 || maxResults > MAX_MAX_RESULTS) {
            throw new GmailRequestInvalidException("maxResults must be between 1 and " + MAX_MAX_RESULTS + ".");
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(API_BASE + "/messages")
                .queryParam("maxResults", maxResults);
        if (!isBlank(pageToken)) {
            builder.queryParam("pageToken", pageToken);
        }
        JsonNode listNode = fetch(builder.build().toUri());

        List<MessageSummary> summaries = new ArrayList<>();
        if (listNode != null && listNode.path("messages").isArray()) {
            for (JsonNode ref : listNode.path("messages")) {
                summaries.add(fetchSummary(ref.path("id").asString()));
            }
        }
        String nextPageToken = listNode == null ? null : listNode.path("nextPageToken").asString(null);
        return new MessageListPage(summaries, nextPageToken);
    }

    public MessageDetail getMessage(String id) {
        if (isBlank(id)) {
            throw new GmailRequestInvalidException("A message id is required.");
        }

        URI uri = UriComponentsBuilder.fromUriString(API_BASE + "/messages/" + encodeMessageId(id))
                .queryParam("format", "full")
                .build().toUri();
        JsonNode node = fetch(uri);
        if (node == null) {
            throw new GmailMessageNotFoundException("No message found with id '" + id + "'.");
        }

        JsonNode payload = node.path("payload");
        return new MessageDetail(node.path("id").asString(), node.path("threadId").asString(),
                header(payload, "Subject"), header(payload, "From"), header(payload, "To"),
                header(payload, "Date"), node.path("snippet").asString(null), extractBodyText(payload));
    }

    private MessageSummary fetchSummary(String id) {
        URI uri = UriComponentsBuilder.fromUriString(API_BASE + "/messages/" + encodeMessageId(id))
                .queryParam("format", "metadata")
                .queryParam("metadataHeaders", "Subject")
                .queryParam("metadataHeaders", "From")
                .queryParam("metadataHeaders", "Date")
                .build().toUri();
        JsonNode node = fetch(uri);
        if (node == null) {
            throw new GmailMessageNotFoundException("No message found with id '" + id + "'.");
        }

        JsonNode payload = node.path("payload");
        return new MessageSummary(node.path("id").asString(), node.path("threadId").asString(),
                header(payload, "Subject"), header(payload, "From"), header(payload, "Date"),
                node.path("snippet").asString(null));
    }

    private JsonNode fetch(URI uri) {
        try {
            return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(authHeaders()), JsonNode.class)
                    .getBody();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (RestClientException e) {
            throw new GmailUpstreamException("Failed to reach Gmail: " + e.getMessage());
        }
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + ensureAccessToken());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private static String header(JsonNode payload, String name) {
        for (JsonNode h : payload.path("headers")) {
            if (name.equalsIgnoreCase(h.path("name").asString())) {
                return h.path("value").asString(null);
            }
        }
        return null;
    }

    private static String extractBodyText(JsonNode payload) {
        String plain = findPart(payload, "text/plain");
        return plain != null ? plain : findPart(payload, "text/html");
    }

    private static String findPart(JsonNode part, String mimeType) {
        if (mimeType.equals(part.path("mimeType").asString("")) && part.path("body").has("data")) {
            return decode(part.path("body").path("data").asString());
        }
        for (JsonNode child : part.path("parts")) {
            String found = findPart(child, mimeType);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String decode(String base64Url) {
        return new String(Base64.getUrlDecoder().decode(base64Url), StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String encodeMessageId(String id) {
        return UriUtils.encodePathSegment(id, StandardCharsets.UTF_8);
    }

    public record MessageSummary(String id, String threadId, String subject, String from, String date,
            String snippet) {
    }

    public record MessageDetail(String id, String threadId, String subject, String from, String to, String date,
            String snippet, String bodyText) {
    }

    public record MessageListPage(List<MessageSummary> messages, String nextPageToken) {
    }
}
