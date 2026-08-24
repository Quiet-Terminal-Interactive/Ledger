package com.quietterminal.ledger;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "ledger.gmail.enabled=true",
        "ledger.gmail.client-id=test-client-id",
        "ledger.gmail.client-secret=test-client-secret",
        "ledger.gmail.refresh-token=test-refresh-token"
})
class EmailIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestTemplate restTemplate;

    private MockRestServiceServer mockGmailServer;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        mockGmailServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
        adminToken = loginAndGetToken("admin", "admin-password");
    }

    @Test
    void listingMessagesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/email/messages"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listingMessagesWithInvalidMaxResultsIsRejected() throws Exception {
        mockMvc.perform(get("/email/messages").param("maxResults", "0")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listingMessagesReturnsSubjectFromAndSnippet() throws Exception {
        stubTokenRefresh();
        mockGmailServer.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=20"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"messages":[{"id":"msg-1","threadId":"thread-1"}]}
                        """, MediaType.APPLICATION_JSON));
        mockGmailServer.expect(requestTo(
                        "https://gmail.googleapis.com/gmail/v1/users/me/messages/msg-1?format=metadata"
                                + "&metadataHeaders=Subject&metadataHeaders=From&metadataHeaders=Date"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":"msg-1","threadId":"thread-1","snippet":"Hello there",
                         "payload":{"headers":[{"name":"Subject","value":"Hi"},
                                                {"name":"From","value":"nat@example.com"},
                                                {"name":"Date","value":"Mon, 1 Jan 2026 00:00:00 +0000"}]}}
                        """, MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/email/messages").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].subject").value("Hi"))
                .andExpect(jsonPath("$.messages[0].from").value("nat@example.com"))
                .andExpect(jsonPath("$.messages[0].snippet").value("Hello there"));
    }

    @Test
    void gettingAMessageReturnsFullDetail() throws Exception {
        stubTokenRefresh();
        mockGmailServer.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/msg-1?format=full"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":"msg-1","threadId":"thread-1","snippet":"Hello there",
                         "payload":{"headers":[{"name":"Subject","value":"Hi"},
                                                {"name":"From","value":"nat@example.com"},
                                                {"name":"To","value":"team@example.com"}],
                                    "mimeType":"text/plain","body":{"data":"aGVsbG8gd29ybGQ"}}}
                        """, MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/email/messages/msg-1").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Hi"))
                .andExpect(jsonPath("$.to").value("team@example.com"))
                .andExpect(jsonPath("$.bodyText").value("hello world"));
    }

    @Test
    void gettingAnUnknownMessageReturnsNotFound() throws Exception {
        stubTokenRefresh();
        mockGmailServer.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/missing?format=full"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/email/messages/missing").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    private void stubTokenRefresh() {
        mockGmailServer.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"access_token":"test-access-token","expires_in":3600,"token_type":"Bearer"}
                        """, MediaType.APPLICATION_JSON));
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        String responseJson = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(responseJson, "$.token");
    }
}
