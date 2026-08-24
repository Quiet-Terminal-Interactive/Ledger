package com.quietterminal.ledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAndGetToken("admin", "admin-password");
    }

    @Test
    void searchingRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/search").param("q", "budget")).andExpect(status().isUnauthorized());
    }

    @Test
    void blankQueryReturnsNoResultsWithoutQueryingTheDatabase() throws Exception {
        mockMvc.perform(get("/search").param("q", "").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
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
