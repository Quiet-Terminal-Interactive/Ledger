package com.quietterminal.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.quietterminal.ledger.error.GmailMessageNotFoundException;
import com.quietterminal.ledger.error.GmailRequestInvalidException;
import com.quietterminal.ledger.error.GmailUpstreamException;
import com.quietterminal.ledger.gmail.GmailService;
import com.quietterminal.ledger.gmail.GmailService.MessageDetail;
import com.quietterminal.ledger.gmail.GmailService.MessageListPage;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class GmailServiceTest {

    private static final URI TOKEN_URI = URI.create("https://oauth2.googleapis.com/token");
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Test
    void refusesToStartWithoutCredentials() {
        RestTemplate restTemplate = mock(RestTemplate.class);

        assertThrows(IllegalStateException.class, () -> new GmailService(restTemplate, "", "secret", "refresh"));
        assertThrows(IllegalStateException.class, () -> new GmailService(restTemplate, "client", "", "refresh"));
        assertThrows(IllegalStateException.class, () -> new GmailService(restTemplate, "client", "secret", ""));
    }

    @Test
    void listingMessagesRejectsOutOfRangeMaxResults() {
        GmailService service = new GmailService(mock(RestTemplate.class), "client", "secret", "refresh");

        assertThrows(GmailRequestInvalidException.class, () -> service.listMessages(0, null));
        assertThrows(GmailRequestInvalidException.class, () -> service.listMessages(51, null));
    }

    @Test
    void listingMessagesFetchesTokenThenSummariesForEachId() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        stubToken(restTemplate);
        stubGet(restTemplate, "https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=20",
                "{\"messages\":[{\"id\":\"msg-1\",\"threadId\":\"thread-1\"}]}");
        stubGet(restTemplate,
                "https://gmail.googleapis.com/gmail/v1/users/me/messages/msg-1?format=metadata"
                        + "&metadataHeaders=Subject&metadataHeaders=From&metadataHeaders=Date",
                "{\"id\":\"msg-1\",\"threadId\":\"thread-1\",\"snippet\":\"Hi there\","
                        + "\"payload\":{\"headers\":[{\"name\":\"Subject\",\"value\":\"Hello\"},"
                        + "{\"name\":\"From\",\"value\":\"nat@example.com\"}]}}");
        GmailService service = new GmailService(restTemplate, "client", "secret", "refresh");

        MessageListPage page = service.listMessages(20, null);

        assertEquals(1, page.messages().size());
        assertEquals("Hello", page.messages().get(0).subject());
        assertEquals("nat@example.com", page.messages().get(0).from());
        assertEquals("Hi there", page.messages().get(0).snippet());
    }

    @Test
    void reusesCachedAccessTokenAcrossCalls() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        stubToken(restTemplate);
        stubGet(restTemplate, "https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=20",
                "{\"messages\":[]}");
        GmailService service = new GmailService(restTemplate, "client", "secret", "refresh");

        service.listMessages(20, null);
        service.listMessages(20, null);

        verify(restTemplate, times(1)).exchange(eq(TOKEN_URI), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(JsonNode.class));
    }

    @Test
    void gettingAMessageDecodesThePlainTextBody() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        stubToken(restTemplate);
        stubGet(restTemplate, "https://gmail.googleapis.com/gmail/v1/users/me/messages/msg-1?format=full",
                "{\"id\":\"msg-1\",\"threadId\":\"thread-1\",\"snippet\":\"Hi there\","
                        + "\"payload\":{\"headers\":[{\"name\":\"Subject\",\"value\":\"Hello\"},"
                        + "{\"name\":\"From\",\"value\":\"nat@example.com\"},"
                        + "{\"name\":\"To\",\"value\":\"team@example.com\"}],"
                        + "\"mimeType\":\"text/plain\",\"body\":{\"data\":\"aGVsbG8gd29ybGQ\"}}}");
        GmailService service = new GmailService(restTemplate, "client", "secret", "refresh");

        MessageDetail detail = service.getMessage("msg-1");

        assertEquals("Hello", detail.subject());
        assertEquals("team@example.com", detail.to());
        assertEquals("hello world", detail.bodyText());
    }

    @Test
    void gettingAMessagePrefersPlainTextOverHtmlAcrossParts() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        stubToken(restTemplate);
        stubGet(restTemplate, "https://gmail.googleapis.com/gmail/v1/users/me/messages/msg-1?format=full",
                "{\"id\":\"msg-1\",\"threadId\":\"thread-1\","
                        + "\"payload\":{\"headers\":[],\"mimeType\":\"multipart/alternative\",\"parts\":["
                        + "{\"mimeType\":\"text/html\",\"body\":{\"data\":\"PGI+aGk8L2I+\"}},"
                        + "{\"mimeType\":\"text/plain\",\"body\":{\"data\":\"aGVsbG8\"}}]}}");
        GmailService service = new GmailService(restTemplate, "client", "secret", "refresh");

        MessageDetail detail = service.getMessage("msg-1");

        assertEquals("hello", detail.bodyText());
    }

    @Test
    void gettingAnUnknownMessageThrowsNotFound() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        stubToken(restTemplate);
        when(restTemplate.exchange(
                eq(URI.create("https://gmail.googleapis.com/gmail/v1/users/me/messages/missing?format=full")),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(HttpClientErrorException.NotFound.create(HttpStatusCode.valueOf(404), "Not Found", null,
                        null, null));
        GmailService service = new GmailService(restTemplate, "client", "secret", "refresh");

        assertThrows(GmailMessageNotFoundException.class, () -> service.getMessage("missing"));
    }

    @Test
    void upstreamFailuresAreWrappedAsGmailUpstreamException() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        stubToken(restTemplate);
        when(restTemplate.exchange(
                eq(URI.create("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=20")),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(new RestClientException("boom"));
        GmailService service = new GmailService(restTemplate, "client", "secret", "refresh");

        assertThrows(GmailUpstreamException.class, () -> service.listMessages(20, null));
    }

    private static void stubToken(RestTemplate restTemplate) {
        JsonNode tokenResponse = MAPPER.readTree("{\"access_token\":\"test-token\",\"expires_in\":3600}");
        when(restTemplate.exchange(eq(TOKEN_URI), eq(HttpMethod.POST), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(tokenResponse));
    }

    private static void stubGet(RestTemplate restTemplate, String uri, String json) {
        JsonNode node = MAPPER.readTree(json);
        when(restTemplate.exchange(eq(URI.create(uri)), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(JsonNode.class))).thenReturn(ResponseEntity.ok(node));
    }
}
