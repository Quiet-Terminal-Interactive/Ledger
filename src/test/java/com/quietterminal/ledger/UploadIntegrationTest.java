package com.quietterminal.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.quietterminal.ledger.entity.Upload;
import com.quietterminal.ledger.error.UploadStorageException;
import com.quietterminal.ledger.repository.UploadRepository;
import com.quietterminal.ledger.storage.MinioUploadStorage;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UploadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UploadRepository uploadRepository;

    @MockitoBean
    private MinioUploadStorage uploadStorage;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAndGetToken("admin", "admin-password");
    }

    @Test
    void uploadingAFileRequiresAuthentication() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/uploads").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void canUploadAFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        String responseJson = mockMvc.perform(multipart("/uploads")
                        .file(file)
                        .param("bucket", "ledger-uploads-test")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("notes.txt"))
                .andExpect(jsonPath("$.contentType").value("text/plain"))
                .andExpect(jsonPath("$.sizeBytes").value(5))
                .andExpect(jsonPath("$.bucket").value("ledger-uploads-test"))
                .andReturn().getResponse().getContentAsString();

        verify(uploadStorage).put(eq("ledger-uploads-test"), anyString(), any(InputStream.class), eq(5L),
                eq("text/plain"));

        String id = JsonPath.read(responseJson, "$.id");
        Upload stored = uploadRepository.findById(java.util.UUID.fromString(id)).orElseThrow();
        assertEquals("notes.txt", stored.getFileName());
        assertEquals("ledger-uploads-test", stored.getBucket());
        assertTrue(stored.getObjectKey().endsWith("-notes.txt"));
    }

    @Test
    void uploadingWithoutAFileIsRejected() throws Exception {
        mockMvc.perform(multipart("/uploads")
                        .param("bucket", "ledger-uploads-test")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadingWithoutABucketIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/uploads")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aStorageFailureIsReportedAsABadGateway() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());
        doThrow(new UploadStorageException("MinIO is unreachable"))
                .when(uploadStorage).put(anyString(), anyString(), any(InputStream.class), anyLong(), anyString());

        mockMvc.perform(multipart("/uploads")
                        .file(file)
                        .param("bucket", "ledger-uploads-test")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadGateway());
    }

    @Test
    void listingUploadsReturnsNewestFirst() throws Exception {
        uploadFile("first.txt");
        uploadFile("second.txt");

        String responseJson = mockMvc.perform(get("/uploads").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> names = JsonPath.read(responseJson, "$[*].fileName");
        assertEquals(List.of("second.txt", "first.txt"), names);
    }

    @Test
    void canCreateADirectory() throws Exception {
        String responseJson = mockMvc.perform(post("/uploads/directory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bucket\":\"ledger-uploads-test\",\"name\":\"reports\"}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("reports"))
                .andExpect(jsonPath("$.contentType").value("application/x-directory"))
                .andExpect(jsonPath("$.sizeBytes").value(0))
                .andExpect(jsonPath("$.bucket").value("ledger-uploads-test"))
                .andReturn().getResponse().getContentAsString();

        verify(uploadStorage).put(eq("ledger-uploads-test"), anyString(), any(InputStream.class), eq(0L),
                eq("application/x-directory"));

        String id = JsonPath.read(responseJson, "$.id");
        Upload stored = uploadRepository.findById(UUID.fromString(id)).orElseThrow();
        assertEquals("reports", stored.getFileName());
        assertTrue(stored.getObjectKey().endsWith("-reports/"));
    }

    @Test
    void creatingADirectoryWithASlashInTheNameIsRejected() throws Exception {
        mockMvc.perform(post("/uploads/directory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bucket\":\"ledger-uploads-test\",\"name\":\"a/b\"}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingAnUploadRemovesItFromStorageAndTheDatabase() throws Exception {
        String responseJson = mockMvc.perform(multipart("/uploads")
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes()))
                        .param("bucket", "ledger-uploads-test")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(responseJson, "$.id");

        mockMvc.perform(delete("/uploads/{id}", id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        verify(uploadStorage).remove(eq("ledger-uploads-test"), anyString());
        assertFalse(uploadRepository.findById(UUID.fromString(id)).isPresent());
    }

    @Test
    void deletingAnUnknownUploadIsNotFound() throws Exception {
        mockMvc.perform(delete("/uploads/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void canUploadIntoAFolderAndListItsContents() throws Exception {
        createDirectory("reports", "");

        mockMvc.perform(multipart("/uploads")
                        .file(new MockMultipartFile("file", "q1.txt", "text/plain", "hello".getBytes()))
                        .param("bucket", "ledger-uploads-test")
                        .param("path", "reports")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentPath").value("reports"));

        uploadFile("outside.txt");

        String rootJson = mockMvc.perform(get("/uploads").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> rootNames = JsonPath.read(rootJson, "$[*].fileName");
        assertTrue(rootNames.contains("reports"));
        assertTrue(rootNames.contains("outside.txt"));
        assertFalse(rootNames.contains("q1.txt"));

        String folderJson = mockMvc.perform(get("/uploads")
                        .param("bucket", "ledger-uploads-test")
                        .param("path", "reports")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> folderNames = JsonPath.read(folderJson, "$[*].fileName");
        assertEquals(List.of("q1.txt"), folderNames);
    }

    @Test
    void uploadingIntoANonExistentFolderIsRejected() throws Exception {
        mockMvc.perform(multipart("/uploads")
                        .file(new MockMultipartFile("file", "q1.txt", "text/plain", "hello".getBytes()))
                        .param("bucket", "ledger-uploads-test")
                        .param("path", "does-not-exist")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void canCreateANestedFolder() throws Exception {
        createDirectory("reports", "");

        String responseJson = mockMvc.perform(post("/uploads/directory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bucket\":\"ledger-uploads-test\",\"name\":\"2024\",\"parentPath\":\"reports\"}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentPath").value("reports"))
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(responseJson, "$.id");
        Upload stored = uploadRepository.findById(UUID.fromString(id)).orElseThrow();
        assertEquals("2024", stored.getFileName());
        assertEquals("reports", stored.getParentPath());
    }

    @Test
    void creatingAFolderUnderANonExistentParentIsRejected() throws Exception {
        mockMvc.perform(post("/uploads/directory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bucket\":\"ledger-uploads-test\",\"name\":\"2024\",\"parentPath\":\"does-not-exist\"}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingAFolderRecursivelyDeletesItsContents() throws Exception {
        String folderId = createDirectory("reports", "");
        String fileResponse = mockMvc.perform(multipart("/uploads")
                        .file(new MockMultipartFile("file", "q1.txt", "text/plain", "hello".getBytes()))
                        .param("bucket", "ledger-uploads-test")
                        .param("path", "reports")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String fileId = JsonPath.read(fileResponse, "$.id");

        mockMvc.perform(delete("/uploads/{id}", folderId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        verify(uploadStorage, org.mockito.Mockito.times(2)).remove(eq("ledger-uploads-test"), anyString());
        assertFalse(uploadRepository.findById(UUID.fromString(folderId)).isPresent());
        assertFalse(uploadRepository.findById(UUID.fromString(fileId)).isPresent());
    }

    private String createDirectory(String name, String parentPath) throws Exception {
        String responseJson = mockMvc.perform(post("/uploads/directory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bucket\":\"ledger-uploads-test\",\"name\":\"" + name + "\",\"parentPath\":\""
                                + parentPath + "\"}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(responseJson, "$.id");
    }

    private void uploadFile(String name) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", name, "text/plain", "hello".getBytes());
        mockMvc.perform(multipart("/uploads")
                        .file(file)
                        .param("bucket", "ledger-uploads-test")
                        .header("Authorization", "Bearer " + adminToken))
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
