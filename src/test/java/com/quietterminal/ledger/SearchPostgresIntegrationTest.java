package com.quietterminal.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;
import com.quietterminal.ledger.entity.User;
import com.quietterminal.ledger.repository.UploadRepository;
import com.quietterminal.ledger.repository.UserCredentialsRepository;
import com.quietterminal.ledger.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ContextConfiguration(initializers = SearchPostgresIntegrationTest.Initializer.class)
@Transactional
@EnabledIf("dockerIsAvailable")
class SearchPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    static boolean dockerIsAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            TestPropertyValues.of(
                    "spring.flyway.enabled=true",
                    "spring.jpa.hibernate.ddl-auto=validate").applyTo(context.getEnvironment());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCredentialsRepository credentialsRepository;

    @Autowired
    private UploadRepository uploadRepository;

    private String adminToken;
    private User admin;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAndGetToken("admin", "admin-password");
        UUID adminId = credentialsRepository.findByUsername("admin").orElseThrow().getUserId();
        admin = userRepository.findById(adminId).orElseThrow();
    }

    @Test
    void findsMatchingTasksWikiPagesAndUploadsAcrossAllThree() throws Exception {
        createTask("Rework budget dashboard", "Needs a chart for monthly spend");
        createWikiPage("finance/budget", "Budget Process", "How the team tracks spend");
        uploadRepository.save(new com.quietterminal.ledger.entity.Upload("budget-report.pdf", "application/pdf",
                1024L, "objects/budget-report.pdf", "ledger-uploads", admin));
        createTask("Unrelated task", "Nothing to do with money");

        String responseJson = mockMvc.perform(get("/search").param("q", "budget")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> types = JsonPath.read(responseJson, "$[*].type");
        List<String> titles = JsonPath.read(responseJson, "$[*].title");
        assertEquals(3, types.size());
        assertTrue(types.contains("TASK"));
        assertTrue(types.contains("WIKI_PAGE"));
        assertTrue(types.contains("UPLOAD"));
        assertTrue(titles.contains("Rework budget dashboard"));
        assertTrue(titles.contains("Budget Process"));
        assertTrue(titles.contains("budget-report.pdf"));
    }

    @Test
    void matchesStemmedVariantsOfAWord() throws Exception {
        createWikiPage("finance/budget", "Budget Process", "How the team tracks running costs.");

        String responseJson = mockMvc.perform(get("/search").param("q", "run")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> titles = JsonPath.read(responseJson, "$[*].title");
        assertEquals(List.of("Budget Process"), titles);
    }

    @Test
    void queryWithNoMatchesReturnsEmptyList() throws Exception {
        createTask("Rework budget dashboard", "Needs a chart for monthly spend");

        mockMvc.perform(get("/search").param("q", "nonexistentterm")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    private void createTask(String title, String description) throws Exception {
        mockMvc.perform(post("/tasks")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"description\":\"" + description + "\"}"))
                .andExpect(status().isCreated());
    }

    private void createWikiPage(String path, String title, String content) throws Exception {
        mockMvc.perform(post("/wiki")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"path\":\"" + path + "\",\"title\":\"" + title + "\",\"content\":\""
                        + content + "\"}"))
                .andExpect(status().isCreated());
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
