package com.quietterminal.ledger;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecoveryCodeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generatingCodesRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/auth/recovery-codes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void statusInitiallyReportsNoCodes() throws Exception {
        String token = loginAndGetToken();

        mockMvc.perform(get("/auth/recovery-codes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.remaining").value(0))
                .andExpect(jsonPath("$.generatedAt").doesNotExist());
    }

    @Test
    void regenerateReturnsTenCodesAndStatusReflectsThem() throws Exception {
        String token = loginAndGetToken();

        List<String> codes = regenerateCodes(token);

        assertTenDistinctCodes(codes);
        mockMvc.perform(get("/auth/recovery-codes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10))
                .andExpect(jsonPath("$.remaining").value(10));
    }

    @Test
    void recoveryLoginWithAValidCodeLogsInAndConsumesTheCode() throws Exception {
        String token = loginAndGetToken();
        List<String> codes = regenerateCodes(token);
        String code = codes.get(0);

        mockMvc.perform(post("/auth/recovery-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());

        mockMvc.perform(get("/auth/recovery-codes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remaining").value(9));
    }

    @Test
    void aRecoveryCodeCannotBeReusedAfterRedemption() throws Exception {
        String token = loginAndGetToken();
        String code = regenerateCodes(token).get(0);

        mockMvc.perform(post("/auth/recovery-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/recovery-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recoveryLoginWithAWrongCodeIsRejected() throws Exception {
        String token = loginAndGetToken();
        regenerateCodes(token);

        mockMvc.perform(post("/auth/recovery-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"code\":\"ZZZZZ-ZZZZZ\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recoveryLoginWithAnUnknownUsernameIsRejected() throws Exception {
        mockMvc.perform(post("/auth/recovery-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"code\":\"ANYTHING-AT-ALL\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void regeneratingInvalidatesThePreviousBatch() throws Exception {
        String token = loginAndGetToken();
        String staleCode = regenerateCodes(token).get(0);
        regenerateCodes(token);

        mockMvc.perform(post("/auth/recovery-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"code\":\"" + staleCode + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokeAllClearsExistingCodes() throws Exception {
        String token = loginAndGetToken();
        regenerateCodes(token);

        mockMvc.perform(delete("/auth/recovery-codes").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/auth/recovery-codes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.remaining").value(0));
    }

    private void assertTenDistinctCodes(List<String> codes) {
        org.junit.jupiter.api.Assertions.assertEquals(10, codes.size());
        org.junit.jupiter.api.Assertions.assertEquals(10, codes.stream().distinct().count());
    }

    private List<String> regenerateCodes(String token) throws Exception {
        String responseJson = mockMvc.perform(post("/auth/recovery-codes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(responseJson, "$.codes");
    }

    private String loginAndGetToken() throws Exception {
        String responseJson = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin-password\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(responseJson, "$.token");
    }
}
