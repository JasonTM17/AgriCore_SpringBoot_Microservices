package com.agricore.farm.api.advice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The advice's own branches, none of which were executed before.
 *
 * <p>Farm is the service where this matters most: it is the one that has carried an
 * {@code @ExceptionHandler(Exception.class)} catch-all the longest, and a catch-all outranks
 * Spring's {@code DefaultHandlerExceptionResolver} — so every framework web exception reached it
 * and was reported as a server fault until the {@code ErrorResponse} re-dispatch was added.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FarmErrorContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void malformedPathVariableIsABadRequest() throws Exception {
                mockMvc.perform(get("/api/v1/farms/not-a-uuid")
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value(containsString("farmId")));
    }

    @Test
    void unknownPathIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/farms-that-do-not-exist")
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unsupportedMethodIsMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/api/v1/plots/" + UUID.randomUUID())
                        .header("X-Dev-User", "manager")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    /**
     * Creating a farm is restricted to SYSTEM_ADMIN and FARM_MANAGER. A denial must carry the
     * platform contract, not Boot's bare 403 page — this is the branch crop-catalog cannot cover,
     * since every one of its endpoints is merely {@code isAuthenticated()}.
     */
    @Test
    void insufficientRoleIsForbiddenInThePlatformShape() throws Exception {
        mockMvc.perform(post("/api/v1/farms")
                        .header("X-Dev-User", "worker")
                        .header("X-Dev-Roles", "FIELD_WORKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"FORBIDDEN-1","name":"Should not be created"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void invalidBodyReportsTheOffendingFields() throws Exception {
        mockMvc.perform(post("/api/v1/farms")
                        .header("X-Dev-User", "manager")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"","name":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations").isArray())
                .andExpect(jsonPath("$.violations[?(@.field=='code')]").exists())
                .andExpect(jsonPath("$.violations[?(@.field=='name')]").exists());
    }

    /**
     * A duplicate code is caught by the service's own existence check and reported as its domain
     * conflict. That is the single-threaded path; the DataIntegrityViolationException handler
     * covers the concurrent one, where two requests both pass the check.
     */
    @Test
    void duplicateFarmCodeReportsTheDomainConflict() throws Exception {
        String body = """
                {"code":"DUPE-%d","name":"First"}
                """.formatted(System.nanoTime());

        mockMvc.perform(post("/api/v1/farms")
                        .header("X-Dev-User", "manager")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/farms")
                        .header("X-Dev-User", "manager")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FARM_CODE_EXISTS"));
    }

    /**
     * An unparseable body is a client error. Without the re-dispatch it reached the catch-all and
     * answered 500, telling the caller the server was broken.
     */
    @Test
    void unreadableBodyIsABadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/farms")
                        .header("X-Dev-User", "manager")
                        .header("X-Dev-Roles", "FARM_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
