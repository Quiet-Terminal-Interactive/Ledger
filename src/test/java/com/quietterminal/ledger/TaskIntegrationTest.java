package com.quietterminal.ledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

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
import com.quietterminal.ledger.entity.Task;
import com.quietterminal.ledger.entity.User;
import com.quietterminal.ledger.entity.UserCredentials;
import com.quietterminal.ledger.enums.ActivityType;
import com.quietterminal.ledger.enums.Permission;
import com.quietterminal.ledger.repository.RoleRepository;
import com.quietterminal.ledger.repository.TaskRepository;
import com.quietterminal.ledger.repository.UserCredentialsRepository;
import com.quietterminal.ledger.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TaskIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCredentialsRepository credentialsRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private UUID adminId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAndGetToken("admin", "admin-password");
        adminId = credentialsRepository.findByUsername("admin").orElseThrow().getUserId();
    }

    private Role memberRole() {
        return roleRepository.findByName("Member").orElseThrow();
    }

    @Test
    void creatingATaskRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Write tests\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void canCreateAMinimalTask() throws Exception {
        mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Write tests\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Write tests"))
                .andExpect(jsonPath("$.status").value("NOTSTARTED"))
                .andExpect(jsonPath("$.blocked").value(false));
    }

    @Test
    void creatingATaskWithABlankTitleIsRejected() throws Exception {
        mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingATaskWithAnUnknownAssigneeIsRejected() throws Exception {
        mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Write tests\",\"assigneeIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingATaskWithAnUnknownBlockerIsRejected() throws Exception {
        mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Write tests\",\"blockedByIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingATaskWithAssigneesRecordsCreatedAndAssignedActivity() throws Exception {
        User member = userRepository.save(new User("Regular", "Member", memberRole()));

        String responseJson = mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Write tests\",\"assigneeIds\":[\"" + member.getUUID() + "\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assigneeIds", org.hamcrest.Matchers.hasSize(1)))
                .andReturn().getResponse().getContentAsString();

        UUID taskId = UUID.fromString(JsonPath.read(responseJson, "$.id"));
        Task task = taskRepository.findById(taskId).orElseThrow();
        assertEquals(2, task.getActivityTracker().size());
        assertEquals(ActivityType.CREATED, task.getActivityTracker().get(0).getActivity());
        assertEquals(ActivityType.ASSIGNED, task.getActivityTracker().get(1).getActivity());
    }

    @Test
    void creatingATaskWithoutAssigneesOnlyRecordsCreatedActivity() throws Exception {
        String responseJson = mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Write tests\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID taskId = UUID.fromString(JsonPath.read(responseJson, "$.id"));
        Task task = taskRepository.findById(taskId).orElseThrow();
        assertEquals(1, task.getActivityTracker().size());
        assertEquals(ActivityType.CREATED, task.getActivityTracker().get(0).getActivity());
    }

    @Test
    void canListTasks() throws Exception {
        createTask("First task");
        createTask("Second task");

        mockMvc.perform(get("/tasks").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void canGetATaskById() throws Exception {
        UUID taskId = createTask("Write tests");

        mockMvc.perform(get("/tasks/" + taskId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Write tests"));
    }

    @Test
    void gettingAnUnknownTaskReturnsNotFound() throws Exception {
        mockMvc.perform(get("/tasks/" + UUID.randomUUID()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void listingTasksAssignedToAnUnknownUserReturnsNotFound() throws Exception {
        mockMvc.perform(get("/tasks/assigned/" + UUID.randomUUID()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void listingTasksAssignedToAUserReturnsOnlyTheirTasks() throws Exception {
        User member = userRepository.save(new User("Regular", "Member", memberRole()));
        mockMvc.perform(post("/tasks")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Assigned task\",\"assigneeIds\":[\"" + member.getUUID() + "\"]}"));
        createTask("Unassigned task");

        mockMvc.perform(get("/tasks/assigned/" + member.getUUID()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Assigned task"));
    }

    @Test
    void canUpdateATasksTitleDescriptionAndDueDate() throws Exception {
        UUID taskId = createTask("Original title");

        mockMvc.perform(patch("/tasks/" + taskId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New title\",\"description\":\"New description\",\"dueDate\":\"2026-01-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New title"))
                .andExpect(jsonPath("$.description").value("New description"))
                .andExpect(jsonPath("$.dueDate").value("2026-01-01"));

        Task task = taskRepository.findById(taskId).orElseThrow();
        List<ActivityType> types = task.getActivityTracker().stream().map(a -> a.getActivity()).toList();
        assertTrue(types.contains(ActivityType.TITLE_UPDATED));
        assertTrue(types.contains(ActivityType.DESCRIPTION_UPDATED));
        assertTrue(types.contains(ActivityType.DUE_DATE_UPDATED));
    }

    @Test
    void updatingAnUnknownTaskReturnsNotFound() throws Exception {
        mockMvc.perform(patch("/tasks/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New title\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void canChangeATasksStatus() throws Exception {
        UUID taskId = createTask("Write tests");

        mockMvc.perform(patch("/tasks/" + taskId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INPROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INPROGRESS"));

        Task task = taskRepository.findById(taskId).orElseThrow();
        assertTrue(task.getActivityTracker().stream().anyMatch(a -> a.getActivity() == ActivityType.STATUS_UPDATED));
    }

    @Test
    void cannotCompleteATaskThatIsStillBlocked() throws Exception {
        UUID blockerId = createTask("Blocker");
        UUID taskId = createTask("Blocked task");
        mockMvc.perform(post("/tasks/" + taskId + "/blockers/" + blockerId)
                .header("Authorization", "Bearer " + adminToken));

        mockMvc.perform(patch("/tasks/" + taskId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changingStatusOfAnUnknownTaskReturnsNotFound() throws Exception {
        mockMvc.perform(patch("/tasks/" + UUID.randomUUID() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INPROGRESS\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void changingStatusWithoutAStatusIsRejected() throws Exception {
        UUID taskId = createTask("Write tests");

        mockMvc.perform(patch("/tasks/" + taskId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assigningAUserToATaskRecordsAnAssignedActivityWithMetadata() throws Exception {
        User member = userRepository.save(new User("Regular", "Member", memberRole()));
        UUID taskId = createTask("Write tests");

        mockMvc.perform(post("/tasks/" + taskId + "/assignees/" + member.getUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeIds", org.hamcrest.Matchers.hasItem(member.getUUID().toString())));

        Task task = taskRepository.findById(taskId).orElseThrow();
        var activity = task.getActivityTracker().get(task.getActivityTracker().size() - 1);
        assertEquals(ActivityType.ASSIGNED, activity.getActivity());
        assertTrue(activity.getMetadata().containsKey("assignedTo"));
    }

    @Test
    void selfAssigningATaskRecordsAClaimedActivity() throws Exception {
        UUID taskId = createTask("Write tests");

        mockMvc.perform(post("/tasks/" + taskId + "/assignees/" + adminId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        Task task = taskRepository.findById(taskId).orElseThrow();
        var activity = task.getActivityTracker().get(task.getActivityTracker().size() - 1);
        assertEquals(ActivityType.CLAIMED, activity.getActivity());
    }

    @Test
    void assigningAnUnknownUserReturnsNotFound() throws Exception {
        UUID taskId = createTask("Write tests");

        mockMvc.perform(post("/tasks/" + taskId + "/assignees/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void assigningToAnUnknownTaskReturnsNotFound() throws Exception {
        User member = userRepository.save(new User("Regular", "Member", memberRole()));

        mockMvc.perform(post("/tasks/" + UUID.randomUUID() + "/assignees/" + member.getUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void unassigningAUserRecordsAnUnassignedActivityWithMetadata() throws Exception {
        User member = userRepository.save(new User("Regular", "Member", memberRole()));
        UUID taskId = createTask("Write tests");
        mockMvc.perform(post("/tasks/" + taskId + "/assignees/" + member.getUUID())
                .header("Authorization", "Bearer " + adminToken));

        mockMvc.perform(delete("/tasks/" + taskId + "/assignees/" + member.getUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeIds", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem(member.getUUID().toString()))));

        Task task = taskRepository.findById(taskId).orElseThrow();
        var activity = task.getActivityTracker().get(task.getActivityTracker().size() - 1);
        assertEquals(ActivityType.UNASSIGNED, activity.getActivity());
        assertTrue(activity.getMetadata().containsKey("removed"));
    }

    @Test
    void unassigningAnUnknownUserReturnsNotFound() throws Exception {
        UUID taskId = createTask("Write tests");

        mockMvc.perform(delete("/tasks/" + taskId + "/assignees/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void canAddABlockerToATask() throws Exception {
        UUID blockerId = createTask("Blocker");
        UUID taskId = createTask("Blocked task");

        mockMvc.perform(post("/tasks/" + taskId + "/blockers/" + blockerId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(true))
                .andExpect(jsonPath("$.blockedByIds", org.hamcrest.Matchers.hasItem(blockerId.toString())));

        Task task = taskRepository.findById(taskId).orElseThrow();
        var activity = task.getActivityTracker().get(task.getActivityTracker().size() - 1);
        assertEquals(ActivityType.BLOCKED, activity.getActivity());
        assertTrue(activity.getMetadata().containsKey("blocker"));
    }

    @Test
    void cannotBlockATaskWithItself() throws Exception {
        UUID taskId = createTask("Write tests");

        mockMvc.perform(post("/tasks/" + taskId + "/blockers/" + taskId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addingAnUnknownBlockerReturnsNotFound() throws Exception {
        UUID taskId = createTask("Write tests");

        mockMvc.perform(post("/tasks/" + taskId + "/blockers/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void addingABlockerToAnUnknownTaskReturnsNotFound() throws Exception {
        UUID blockerId = createTask("Blocker");

        mockMvc.perform(post("/tasks/" + UUID.randomUUID() + "/blockers/" + blockerId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void canRemoveABlockerFromATask() throws Exception {
        UUID blockerId = createTask("Blocker");
        UUID taskId = createTask("Blocked task");
        mockMvc.perform(post("/tasks/" + taskId + "/blockers/" + blockerId)
                .header("Authorization", "Bearer " + adminToken));

        mockMvc.perform(delete("/tasks/" + taskId + "/blockers/" + blockerId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(false));

        Task task = taskRepository.findById(taskId).orElseThrow();
        var activity = task.getActivityTracker().get(task.getActivityTracker().size() - 1);
        assertEquals(ActivityType.UNBLOCKED, activity.getActivity());
    }

    @Test
    void removingAnUnknownBlockerReturnsNotFound() throws Exception {
        UUID taskId = createTask("Write tests");

        mockMvc.perform(delete("/tasks/" + taskId + "/blockers/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void canDeleteATask() throws Exception {
        UUID taskId = createTask("Write tests");

        mockMvc.perform(delete("/tasks/" + taskId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/tasks/" + taskId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingAnUnknownTaskReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/tasks/" + UUID.randomUUID()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void aUserWithOnlyTasksReadCannotCreateATask() throws Exception {
        Role readOnly = roleRepository.save(new Role("Task Viewer", Set.of(Permission.TASKS_READ)));
        User user = userRepository.save(new User("Read", "Only", readOnly));
        credentialsRepository.save(new UserCredentials(user.getUUID(), "read-only",
                passwordEncoder.encode("read-only-password")));
        String token = loginAndGetToken("read-only", "read-only-password");

        mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Write tests\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/tasks").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private UUID createTask(String title) throws Exception {
        String responseJson = mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(JsonPath.read(responseJson, "$.id"));
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
