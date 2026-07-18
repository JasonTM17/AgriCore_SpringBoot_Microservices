package com.agricore.work;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkErrorBoundaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void malformedJson_returnsStableBadRequest() throws Exception {
        assertApiError(
                mockMvc.perform(authenticated(post("/api/v1/work-tasks"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{")),
                HttpStatus.BAD_REQUEST,
                "MALFORMED_JSON"
        );
    }

    @Test
    void unsupportedMediaType_returnsStableApiError() throws Exception {
        assertApiError(
                mockMvc.perform(authenticated(post("/api/v1/work-tasks"))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}")),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE"
        );
    }

    @Test
    void invalidPathIdentifier_returnsStableBadRequest() throws Exception {
        assertApiError(
                mockMvc.perform(authenticated(get("/api/v1/work-tasks/not-a-uuid"))),
                HttpStatus.BAD_REQUEST,
                "INVALID_ARGUMENT"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"?page=-1", "?size=0", "?size=101"})
    void invalidPaging_returnsValidationError(String query) throws Exception {
        assertApiError(
                mockMvc.perform(authenticated(get("/api/v1/work-tasks" + query))),
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED"
        );
    }

    @Test
    void priorityBeyondStorageLimit_isRejectedBeforePersistence() throws Exception {
        assertApiError(
                mockMvc.perform(authenticated(post("/api/v1/work-tasks"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"WT-%s",
                                  "cropCycleId":"%s",
                                  "plotId":"%s",
                                  "taskType":"IRRIGATION",
                                  "title":"Boundary test",
                                  "priority":"%s"
                                }
                                """.formatted(
                                System.nanoTime(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "P".repeat(33)))),
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED"
        );
    }

    private static void assertApiError(ResultActions result, HttpStatus expectedStatus, String expectedCode)
            throws Exception {
        result.andExpect(status().is(expectedStatus.value()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.status").value(expectedStatus.value()))
                .andExpect(jsonPath("$.error").value(expectedStatus.getReasonPhrase()))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.path").isString());
    }

    private static MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request
                .header("X-Dev-User", "manager")
                .header("X-Dev-Roles", "FARM_MANAGER");
    }
}
