package com.agricore.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class WebAuthCookieIntegrationTest {

    private static final String ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void webLoginRefreshLogout_usesHttpOnlyCookieAndKeepsRefreshOutOfBody() throws Exception {
        String email = "webauth" + System.nanoTime() + "@agricore.test";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123!","fullName":"Web Auth User"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/web/login")
                        .header("Origin", ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123!"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(cookie().exists("agricore_refresh"))
                .andExpect(cookie().httpOnly("agricore_refresh", true))
                .andExpect(cookie().path("agricore_refresh", "/api/v1/auth/web"))
                .andReturn();

        MockHttpServletResponse loginResponse = loginResult.getResponse();
        JsonNode loginJson = objectMapper.readTree(loginResponse.getContentAsString());
        assertThat(loginJson.has("refreshToken")).isFalse();
        String accessToken = loginJson.get("accessToken").asText();
        Cookie refreshCookie = loginResponse.getCookie("agricore_refresh");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getValue()).isNotBlank();
        assertThat(refreshCookie.isHttpOnly()).isTrue();

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/web/refresh")
                        .header("Origin", ORIGIN)
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().exists("agricore_refresh"))
                .andReturn();

        Cookie rotated = refreshResult.getResponse().getCookie("agricore_refresh");
        assertThat(rotated).isNotNull();
        assertThat(rotated.getValue()).isNotEqualTo(refreshCookie.getValue());

        mockMvc.perform(post("/api/v1/auth/web/logout")
                        .header("Origin", ORIGIN)
                        .cookie(rotated))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("agricore_refresh", 0));

        mockMvc.perform(post("/api/v1/auth/web/refresh")
                        .header("Origin", ORIGIN)
                        .cookie(rotated))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webLogin_rejectsDisallowedOrigin() throws Exception {
        String email = "origin" + System.nanoTime() + "@agricore.test";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123!","fullName":"Origin User"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/web/login")
                        .header("Origin", "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123!"}
                                """.formatted(email)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_FORBIDDEN"));
    }

    @Test
    void webLogin_requiresBrowserOriginBeforeProcessingCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/web/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"origin-required@agricore.test","password":"Secret123!"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_REQUIRED"));
    }

    @Test
    void webRefresh_withoutCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/web/refresh")
                        .header("Origin", ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }
}
