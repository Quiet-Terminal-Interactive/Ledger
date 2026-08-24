package com.quietterminal.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.quietterminal.ledger.repository.WikiPageRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WikiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WikiPageRepository wikiPageRepository;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAndGetToken("admin", "admin-password");
    }

    @Test
    void creatingAPageRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/wiki")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"guides/setup\",\"title\":\"Setup\",\"content\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void canCreateAPage() throws Exception {
        String responseJson = mockMvc.perform(post("/wiki")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"/Guides/Setup/\",\"title\":\"Setup Guide\",\"content\":\"# Hello\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.path").value("guides/setup"))
                .andExpect(jsonPath("$.title").value("Setup Guide"))
                .andExpect(jsonPath("$.content").value("# Hello"))
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(responseJson, "$.id");
        assertEquals("guides/setup", wikiPageRepository.findById(java.util.UUID.fromString(id)).orElseThrow().getPath());
    }

    @Test
    void creatingAPageWithABlankTitleIsRejected() throws Exception {
        mockMvc.perform(post("/wiki")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"guides/setup\",\"title\":\"\",\"content\":\"hi\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingAPageAtADuplicatePathIsRejected() throws Exception {
        createPage("guides/setup", "Setup Guide");

        mockMvc.perform(post("/wiki")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"guides/setup\",\"title\":\"Other\",\"content\":\"hi\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listingPagesReturnsThemOrderedByPath() throws Exception {
        createPage("guides/zeta", "Zeta");
        createPage("guides/alpha", "Alpha");

        String responseJson = mockMvc.perform(get("/wiki").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> paths = JsonPath.read(responseJson, "$[*].path");
        assertEquals(List.of("guides/alpha", "guides/zeta"), paths);
    }

    @Test
    void canFetchAPageByPath() throws Exception {
        createPage("guides/setup", "Setup Guide");

        mockMvc.perform(get("/wiki/by-path").param("path", "guides/setup")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Setup Guide"));
    }

    @Test
    void fetchingAMissingPageByPathIsNotFound() throws Exception {
        mockMvc.perform(get("/wiki/by-path").param("path", "does/not/exist")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void canUpdatePageTitleAndContent() throws Exception {
        String id = createPage("guides/setup", "Setup Guide");

        mockMvc.perform(patch("/wiki/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New Title\",\"content\":\"new content\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Title"))
                .andExpect(jsonPath("$.content").value("new content"));
    }

    @Test
    void updatingAMissingPageIsNotFound() throws Exception {
        mockMvc.perform(patch("/wiki/" + java.util.UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New Title\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void canDeleteAPage() throws Exception {
        String id = createPage("guides/setup", "Setup Guide");

        mockMvc.perform(delete("/wiki/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/wiki/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    private String createPage(String path, String title) throws Exception {
        String responseJson = mockMvc.perform(post("/wiki")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"" + path + "\",\"title\":\"" + title + "\",\"content\":\"hi\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(responseJson, "$.id");
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
