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
import com.quietterminal.ledger.repository.BudgetEntryRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BudgetIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BudgetEntryRepository budgetEntryRepository;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAndGetToken("admin", "admin-password");
    }

    @Test
    void creatingAnEntryRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hosting\",\"description\":\"VPS\",\"amount\":5.00}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void canCreateAnEntry() throws Exception {
        String responseJson = mockMvc.perform(post("/budget")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hosting\",\"description\":\"VPS\",\"amount\":5.00,\"type\":\"EXPENSE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Hosting"))
                .andExpect(jsonPath("$.description").value("VPS"))
                .andExpect(jsonPath("$.amount").value(5.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(responseJson, "$.id");
        assertEquals("Hosting", budgetEntryRepository.findById(java.util.UUID.fromString(id)).orElseThrow().getName());
    }

    @Test
    void creatingAnEntryWithABlankNameIsRejected() throws Exception {
        mockMvc.perform(post("/budget")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"description\":\"VPS\",\"amount\":5.00,\"type\":\"EXPENSE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingAnEntryWithANegativeAmountIsRejected() throws Exception {
        mockMvc.perform(post("/budget")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hosting\",\"description\":\"VPS\",\"amount\":-5.00,\"type\":\"EXPENSE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listingEntriesReturnsThemOrderedByName() throws Exception {
        createEntry("Zeta Sub", "1.00");
        createEntry("Alpha Sub", "2.00");

        String responseJson = mockMvc.perform(get("/budget").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> names = JsonPath.read(responseJson, "$[*].name");
        assertEquals(List.of("Alpha Sub", "Zeta Sub"), names);
    }

    @Test
    void fetchingAMissingEntryIsNotFound() throws Exception {
        mockMvc.perform(get("/budget/" + java.util.UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void canUpdateEntryNameDescriptionAndAmount() throws Exception {
        String id = createEntry("Hosting", "5.00");

        mockMvc.perform(patch("/budget/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Hosting\",\"description\":\"new desc\",\"amount\":7.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Hosting"))
                .andExpect(jsonPath("$.description").value("new desc"))
                .andExpect(jsonPath("$.amount").value(7.50));
    }

    @Test
    void updatingAMissingEntryIsNotFound() throws Exception {
        mockMvc.perform(patch("/budget/" + java.util.UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Hosting\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void canDeleteAnEntry() throws Exception {
        String id = createEntry("Hosting", "5.00");

        mockMvc.perform(delete("/budget/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/budget/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    private String createEntry(String name, String amount) throws Exception {
        String responseJson = mockMvc.perform(post("/budget")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"description\":\"d\",\"amount\":" + amount
                                + ",\"type\":\"EXPENSE\"}"))
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
