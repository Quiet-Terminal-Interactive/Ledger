package com.quietterminal.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.util.List;

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
