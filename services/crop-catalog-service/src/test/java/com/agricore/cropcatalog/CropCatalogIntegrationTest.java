package com.agricore.cropcatalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CropCatalogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listSeededCrops() throws Exception {
        mockMvc.perform(get("/api/v1/crops")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.content[?(@.code=='RICE_ST25')]").exists());
    }

    @Test
    void getByCode() throws Exception {
        mockMvc.perform(get("/api/v1/crops/by-code/COFFEE_ROBUSTA")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cà phê Robusta"));
    }

    /**
     * The status was always right; the body was not. Without an advice the service falls through
     * to Boot's default error controller, which emits no {@code code} and no {@code message}, so a
     * client written against the platform {@code ApiError} contract reads null from this service
     * alone.
     */
    @Test
    void unknownCropId_returnsPlatformErrorContract() throws Exception {
        mockMvc.perform(get("/api/v1/crops/" + UUID.randomUUID())
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Crop not found"))
                .andExpect(jsonPath("$.path").value(startsWith("/api/v1/crops/")))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void unknownCropCode_returnsPlatformErrorContract() throws Exception {
        mockMvc.perform(get("/api/v1/crops/by-code/NOT_A_REAL_CROP")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    /**
     * Declaring {@code @ExceptionHandler(Exception.class)} takes precedence over Spring's
     * {@code DefaultHandlerExceptionResolver}, so a catch-all silently captures the framework's own
     * web exceptions — the ones that already carry a correct status. Left unhandled, all four of
     * these answered 500: a mistyped URL read as an outage.
     */
    @Test
    void malformedPathVariable_isABadRequestNotAServerError() throws Exception {
        mockMvc.perform(get("/api/v1/crops/not-a-uuid")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value(containsString("cropId")));
    }

    @Test
    void malformedQueryParameter_isABadRequestNotAServerError() throws Exception {
        mockMvc.perform(get("/api/v1/crops?page=abc")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void unknownPath_isNotFoundNotAServerError() throws Exception {
        mockMvc.perform(get("/api/v1/no-such-endpoint")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unsupportedMethod_isMethodNotAllowedNotAServerError() throws Exception {
        mockMvc.perform(post("/api/v1/crops")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    /**
     * The message names the parameter this service defines rather than repeating what the caller
     * typed. {@code path} still carries the request URI — that is the contract for every error on
     * the platform, and the URI is the caller's own request — but the human-readable message does
     * not add a second copy of an unvalidated value.
     */
    @Test
    void invalidParameterMessage_namesTheParameterRatherThanItsValue() throws Exception {
        mockMvc.perform(get("/api/v1/crops/caller-supplied-garbage")
                        .header("X-Dev-User", "agronomist")
                        .header("X-Dev-Roles", "AGRONOMIST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("cropId")))
                .andExpect(jsonPath("$.message").value(not(containsString("caller-supplied-garbage"))));
    }
}
