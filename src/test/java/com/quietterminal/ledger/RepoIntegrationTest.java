package com.quietterminal.ledger;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RepoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestTemplate restTemplate;

    private MockRestServiceServer mockGitServer;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        mockGitServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
        adminToken = loginAndGetToken("admin", "admin-password");
    }

    @Test
    void listingReposRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/repos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listingReposForAnUnlistedOwnerReturnsNotFound() throws Exception {
        mockMvc.perform(get("/repos/someoneelse").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void listingReposForAListedOwnerOnlyReturnsPublicRepos() throws Exception {
        mockGitServer.expect(requestTo("https://git.quietterminal.co.uk/api/v1/users/KohanMathers/repos?limit=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {"name":"public-repo","private":false,"description":"visible",
                           "default_branch":"main","owner":{"login":"KohanMathers"}},
                          {"name":"private-repo","private":true,"description":"hidden",
                           "default_branch":"main","owner":{"login":"KohanMathers"}}
                        ]
                        """, MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/repos/KohanMathers").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("public-repo"));
    }

    @Test
    void aggregatedRepoListingOnlyReturnsPublicRepos() throws Exception {
        mockGitServer.expect(requestTo("https://git.quietterminal.co.uk/api/v1/users/KohanMathers/repos?limit=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"name":"public-repo","private":false,"owner":{"login":"KohanMathers"}}]
                        """, MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/repos").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("public-repo"));
    }

    @Test
    void treeOnAPrivateRepoReturnsNotFound() throws Exception {
        mockGitServer.expect(requestTo("https://git.quietterminal.co.uk/api/v1/repos/KohanMathers/secret-repo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"private\":true}", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/repos/KohanMathers/secret-repo/tree").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void treeOnAPublicRepoReturnsDirectoryListing() throws Exception {
        mockGitServer.expect(requestTo("https://git.quietterminal.co.uk/api/v1/repos/KohanMathers/open-repo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"private\":false}", MediaType.APPLICATION_JSON));
        mockGitServer.expect(requestTo("https://git.quietterminal.co.uk/api/v1/repos/KohanMathers/open-repo/contents"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"name":"README.md","path":"README.md","type":"file","size":42}]
                        """, MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/repos/KohanMathers/open-repo/tree").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("README.md"));
    }

    @Test
    void fileOnAPublicRepoReturnsFileContent() throws Exception {
        mockGitServer.expect(requestTo("https://git.quietterminal.co.uk/api/v1/repos/KohanMathers/open-repo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"private\":false}", MediaType.APPLICATION_JSON));
        mockGitServer.expect(requestTo(
                        "https://git.quietterminal.co.uk/api/v1/repos/KohanMathers/open-repo/contents/README.md"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"name":"README.md","path":"README.md","size":11,"encoding":"base64","content":"aGVsbG8gd29ybGQ="}
                        """, MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/repos/KohanMathers/open-repo/file")
                        .param("path", "README.md")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("README.md"))
                .andExpect(jsonPath("$.content").value("aGVsbG8gd29ybGQ="));
    }

    @Test
    void fileEndpointWithoutAPathIsRejected() throws Exception {
        mockMvc.perform(get("/repos/KohanMathers/open-repo/file").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
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
