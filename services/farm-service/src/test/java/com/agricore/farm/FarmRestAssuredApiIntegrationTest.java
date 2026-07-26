package com.agricore.farm;

import io.restassured.http.ContentType;
import io.restassured.module.mockmvc.specification.MockMvcRequestSpecification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FarmRestAssuredApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listFarmsSupportsFilteringAndPagination() {
        String owner = UUID.randomUUID().toString();
        createFarm(owner, "DLA-" + compactId(), "Dak Lak");
        createFarm(owner, "LDG-" + compactId(), "Lam Dong");

        manager(owner)
                .queryParam("province", "Dak Lak")
                .queryParam("status", "active")
                .queryParam("page", 0)
                .queryParam("size", 1)
                .queryParam("sort", "code,asc")
                .when()
                .get("/api/v1/farms")
                .then()
                .statusCode(200)
                .body("totalElements", equalTo(1))
                .body("content", hasSize(1))
                .body("content[0].province", equalTo("Dak Lak"));
    }

    @Test
    void createFarmRejectsInvalidInput() {
        manager(UUID.randomUUID().toString())
                .body("""
                        {"name":"Missing code"}
                        """)
                .when()
                .post("/api/v1/farms")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_FAILED"));
    }

    @Test
    void listFarmsRejectsMissingAuthentication() {
        given()
                .mockMvc(mockMvc)
                .when()
                .get("/api/v1/farms")
                .then()
                .statusCode(401);
    }

    @Test
    void createFarmRequiresWritePermission() {
        manager(UUID.randomUUID().toString())
                .header("X-Dev-Permissions", "FARM_READ")
                .body("""
                        {"code":"DENIED-%s","name":"Permission denied"}
                        """.formatted(compactId()))
                .when()
                .post("/api/v1/farms")
                .then()
                .statusCode(403);
    }

    @Test
    void getFarmReturnsNotFoundForUnknownIdentifier() {
        systemAdmin()
                .when()
                .get("/api/v1/farms/{farmId}", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", equalTo("FARM_NOT_FOUND"));
    }

    @Test
    void duplicateFarmCodeReturnsConflict() {
        String owner = UUID.randomUUID().toString();
        String code = "DUP-" + compactId();
        createFarm(owner, code, "Dak Lak");

        manager(owner)
                .body(farmBody(code, "Dak Lak"))
                .when()
                .post("/api/v1/farms")
                .then()
                .statusCode(409)
                .body("code", equalTo("FARM_CODE_EXISTS"));
    }

    private String createFarm(String owner, String code, String province) {
        return manager(owner)
                .body(farmBody(code, province))
                .when()
                .post("/api/v1/farms")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private MockMvcRequestSpecification manager(String subject) {
        return given()
                .mockMvc(mockMvc)
                .header("X-Dev-User", subject)
                .header("X-Dev-Roles", "FARM_MANAGER")
                .contentType(ContentType.JSON);
    }

    private MockMvcRequestSpecification systemAdmin() {
        return given()
                .mockMvc(mockMvc)
                .header("X-Dev-User", "system-admin")
                .header("X-Dev-Roles", "SYSTEM_ADMIN")
                .contentType(ContentType.JSON);
    }

    private static String farmBody(String code, String province) {
        return """
                {
                  "code":"%s",
                  "name":"REST Assured Farm",
                  "province":"%s",
                  "totalAreaHa":12.5
                }
                """.formatted(code, province);
    }

    private static String compactId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
