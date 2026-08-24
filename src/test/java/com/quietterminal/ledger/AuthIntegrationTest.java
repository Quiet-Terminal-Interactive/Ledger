package com.quietterminal.ledger;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.quietterminal.ledger.entity.User;
import com.quietterminal.ledger.entity.UserCredentials;
import com.quietterminal.ledger.repository.RoleRepository;
import com.quietterminal.ledger.repository.UserCredentialsRepository;
import com.quietterminal.ledger.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCredentialsRepository credentialsRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void healthEndpointIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointRejectsRequestsWithoutAToken() throws Exception {
        mockMvc.perform(get("/some-protected-path"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bootstrappedAdminCanLogInAndReceivesAJwt() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not("")));
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithUnknownUsernameIsRejected() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"whatever\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenIsAcceptedByTheFilterChain() throws Exception {
        String token = loginAndGetToken("admin", "admin-password");

        mockMvc.perform(get("/some-unmapped-path")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void loginCreatesASessionListedByTheSessionsEndpoint() throws Exception {
        String token = loginAndGetToken("admin", "admin-password");

        mockMvc.perform(get("/auth/sessions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].current").value(true));
    }

    @Test
    void logoutRevokesTheTokenUsedToCallIt() throws Exception {
        String token = loginAndGetToken("admin", "admin-password");

        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/auth/sessions").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aSessionCanBeRevokedFromAnotherSession() throws Exception {
        String firstToken = loginAndGetToken("admin", "admin-password");
        String secondToken = loginAndGetToken("admin", "admin-password");

        String sessionsJson = mockMvc.perform(get("/auth/sessions").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn().getResponse().getContentAsString();

        List<String> currentSessionIds = JsonPath.read(sessionsJson, "$[?(@.current == true)].id");
        String firstSessionId = currentSessionIds.get(0);

        mockMvc.perform(delete("/auth/sessions/" + firstSessionId)
                        .header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/auth/sessions").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/auth/sessions").header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void adminCheckReturnsTrueForAnAdmin() throws Exception {
        String token = loginAndGetToken("admin", "admin-password");

        mockMvc.perform(get("/admin/check").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canManageUsers").value(true));
    }

    @Test
    void adminCheckReturnsFalseForAMember() throws Exception {
        User member = userRepository.save(new User("Regular", "Member", roleRepository.findByName("Member").orElseThrow()));
        credentialsRepository.save(new UserCredentials(member.getUUID(), "regular",
                passwordEncoder.encode("regular-password")));

        String token = loginAndGetToken("regular", "regular-password");

        mockMvc.perform(get("/admin/check").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canManageUsers").value(false));
    }

    @Test
    void adminCheckRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/admin/check"))
                .andExpect(status().isUnauthorized());
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
