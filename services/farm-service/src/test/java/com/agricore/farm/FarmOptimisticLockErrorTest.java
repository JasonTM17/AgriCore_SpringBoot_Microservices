package com.agricore.farm;

import com.agricore.farm.api.advice.GlobalExceptionHandler;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.JpaOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FarmOptimisticLockErrorTest {

    @Test
    void jpaOptimisticLockFailureReturnsActionableConflict() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ConflictController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/test/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK"))
                .andExpect(jsonPath("$.message").value(
                        "Farm data changed concurrently; reload the latest state before retrying"
                ))
                .andExpect(jsonPath("$.path").value("/test/optimistic-lock"));
    }

    @RestController
    static class ConflictController {

        @PostMapping("/test/optimistic-lock")
        void conflict() {
            throw new JpaOptimisticLockingFailureException(new OptimisticLockException("stale version"));
        }
    }
}
