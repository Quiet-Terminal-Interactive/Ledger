package com.quietterminal.ledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.quietterminal.ledger.entity.Role;
import com.quietterminal.ledger.entity.User;
import com.quietterminal.ledger.entity.UserCredentials;
import com.quietterminal.ledger.enums.Permission;
import com.quietterminal.ledger.repository.RoleRepository;
import com.quietterminal.ledger.repository.UserCredentialsRepository;
import com.quietterminal.ledger.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoleControllerTest {

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

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAndGetToken("admin", "admin-password");
    }

    @Test
    void creatingARoleRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Support\",\"permissions\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void creatingARoleRequiresRolesManagePermission() throws Exception {
        Role restricted = roleRepository.save(new Role("Restricted", Set.of(Permission.TASKS_READ)));
        String token = createUserWithRoleAndLogIn(restricted);

        mockMvc.perform(post("/roles")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Support\",\"permissions\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateARoleWithPermissions() throws Exception {
        mockMvc.perform(post("/roles")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Support\",\"permissions\":[\"TASKS_READ\",\"WIKI_READ\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Support"))
                .andExpect(jsonPath("$.builtIn").value(false))
                .andExpect(
                        jsonPath("$.permissions", org.hamcrest.Matchers.containsInAnyOrder("TASKS_READ", "WIKI_READ")));
    }

    @Test
    void creatingARoleWithADuplicateNameIsRejected() throws Exception {
        mockMvc.perform(post("/roles")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Member\",\"permissions\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void canListRoles() throws Exception {
        mockMvc.perform(get("/roles").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void canUpdateARolesNameAndPermissions() throws Exception {
        Role role = roleRepository.save(new Role("Support", Set.of(Permission.TASKS_READ)));

        mockMvc.perform(patch("/roles/" + role.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Support Lead\",\"permissions\":[\"TASKS_READ\",\"TASKS_WRITE\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Support Lead"))
                .andExpect(jsonPath("$.permissions",
                        org.hamcrest.Matchers.containsInAnyOrder("TASKS_READ", "TASKS_WRITE")));
    }

    @Test
    void updatingAnUnknownRoleReturnsNotFound() throws Exception {
        mockMvc.perform(patch("/roles/" + java.util.UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New name\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingTheBuiltInAdminRoleIsRejected() throws Exception {
        Role admin = roleRepository.findByName("Admin").orElseThrow();

        mockMvc.perform(delete("/roles/" + admin.getId()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void deletingARoleStillAssignedToAUserReturnsConflict() throws Exception {
        Role role = roleRepository.save(new Role("Support", Set.of(Permission.TASKS_READ)));
        userRepository.save(new User("Support", "Person", role));
        userRepository.flush();

        mockMvc.perform(delete("/roles/" + role.getId()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void canDeleteAnUnusedNonBuiltInRole() throws Exception {
        Role role = roleRepository.save(new Role("Support", Set.of(Permission.TASKS_READ)));

        mockMvc.perform(delete("/roles/" + role.getId()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    private String createUserWithRoleAndLogIn(Role role) throws Exception {
        User user = userRepository.save(new User("Restricted", "User", role));
        credentialsRepository.save(new UserCredentials(user.getUUID(), "restricted-user",
                passwordEncoder.encode("restricted-password")));
        return loginAndGetToken("restricted-user", "restricted-password");
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
