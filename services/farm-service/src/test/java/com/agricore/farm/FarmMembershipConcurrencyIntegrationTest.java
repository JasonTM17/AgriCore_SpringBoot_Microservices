package com.agricore.farm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FarmMembershipConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void concurrentRevokes_cannotRemoveFinalMembership() throws Exception {
        String owner = UUID.randomUUID().toString();
        String worker = UUID.randomUUID().toString();
        String farmId = createFarm(owner);
        grant(farmId, owner, worker);
        List<String> membershipIds = listMembershipIds(farmId);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (String membershipId : membershipIds) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent revoke start timed out");
                    }
                    return mockMvc.perform(delete(
                                    "/api/v1/farms/{farmId}/memberships/{membershipId}",
                                    farmId,
                                    membershipId
                            ).headers(devAuth("system-admin", "SYSTEM_ADMIN")))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<Integer> statuses = futures.stream()
                    .map(future -> getStatus(future))
                    .sorted()
                    .toList();
            assertEquals(List.of(204, 409), statuses);
        } finally {
            executor.shutdownNow();
        }

        mockMvc.perform(get("/api/v1/farms/{farmId}/memberships", farmId)
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void concurrentDuplicateGrants_createExactlyOneMembership() throws Exception {
        String owner = UUID.randomUUID().toString();
        String target = UUID.randomUUID().toString();
        String farmId = createFarm(owner);

        List<Integer> statuses = runConcurrently(
                () -> grantStatus(farmId, owner, target),
                () -> grantStatus(farmId, owner, target)
        );
        assertEquals(List.of(201, 409), statuses);

        mockMvc.perform(get("/api/v1/farms/{farmId}/memberships", farmId)
                        .headers(devAuth(owner, "FARM_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    private String createFarm(String owner) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/farms")
                        .headers(devAuth(owner, "FARM_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"F-%s","name":"Concurrency Farm"}
                                """.formatted(UUID.randomUUID().toString().replace("-", ""))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void grant(String farmId, String owner, String subject) throws Exception {
        mockMvc.perform(post("/api/v1/farms/{farmId}/memberships", farmId)
                        .headers(devAuth(owner, "FARM_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"%s"}
                                """.formatted(subject)))
                .andExpect(status().isCreated());
    }

    private int grantStatus(String farmId, String owner, String subject) {
        try {
            return mockMvc.perform(post("/api/v1/farms/{farmId}/memberships", farmId)
                            .headers(devAuth(owner, "FARM_MANAGER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"subject":"%s"}
                                    """.formatted(subject)))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        } catch (Exception ex) {
            throw new AssertionError("Concurrent grant failed", ex);
        }
    }

    private List<Integer> runConcurrently(IntSupplier first, IntSupplier second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> futures = List.of(
                    submitConcurrent(executor, ready, start, first),
                    submitConcurrent(executor, ready, start, second)
            );
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return futures.stream().map(FarmMembershipConcurrencyIntegrationTest::getStatus).sorted().toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private Future<Integer> submitConcurrent(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            IntSupplier action
    ) {
        return executor.submit(() -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent action start timed out");
            }
            return action.getAsInt();
        });
    }

    private List<String> listMembershipIds(String farmId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/farms/{farmId}/memberships", farmId)
                        .headers(devAuth("system-admin", "SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn();
        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
        List<String> ids = new ArrayList<>();
        content.forEach(membership -> ids.add(membership.get("id").asText()));
        return ids;
    }

    private static int getStatus(Future<Integer> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new AssertionError("Concurrent revoke failed", ex);
        }
    }

    private static HttpHeaders devAuth(String subject, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Dev-User", subject);
        headers.set("X-Dev-Roles", role);
        return headers;
    }
}
