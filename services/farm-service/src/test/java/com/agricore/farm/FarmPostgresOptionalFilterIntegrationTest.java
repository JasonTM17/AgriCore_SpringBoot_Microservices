package com.agricore.farm;

import com.agricore.farm.infrastructure.persistence.EnterpriseJpaRepository;
import com.agricore.farm.infrastructure.persistence.FarmAreaJpaRepository;
import com.agricore.farm.infrastructure.persistence.FarmJpaRepository;
import com.agricore.farm.infrastructure.persistence.IrrigationZoneJpaRepository;
import com.agricore.farm.infrastructure.persistence.PlotJpaRepository;
import com.agricore.farm.infrastructure.persistence.SoilProfileJpaRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FarmPostgresOptionalFilterIntegrationTest {

    private static final String COMPOSE_JDBC =
            "jdbc:postgresql://127.0.0.1:5434/agricore_farm";
    private static final String DATABASE_USER = "agricore";
    private static final String DATABASE_PASSWORD = "agricore_dev_change_me";

    private static PostgreSQLContainer<?> container;
    private static String jdbcUrl;
    private static String username;
    private static String password;

    static {
        if (isComposePostgresUp()) {
            jdbcUrl = COMPOSE_JDBC;
            username = DATABASE_USER;
            password = DATABASE_PASSWORD;
        } else if (tryStartTestcontainer()) {
            jdbcUrl = container.getJdbcUrl();
            username = container.getUsername();
            password = container.getPassword();
        } else {
            throw new IllegalStateException(
                    "PostgreSQL is required: start Docker Desktop or the Compose postgres service");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EnterpriseJpaRepository enterpriseRepository;

    @Autowired
    private FarmJpaRepository farmRepository;

    @Autowired
    private PlotJpaRepository plotRepository;

    @Autowired
    private FarmAreaJpaRepository areaRepository;

    @Autowired
    private SoilProfileJpaRepository soilProfileRepository;

    @Autowired
    private IrrigationZoneJpaRepository irrigationZoneRepository;

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add(
                "spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect"
        );
    }

    @AfterAll
    static void stopContainer() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void listFarmsWithoutOptionalFiltersExecutesOnPostgres() throws Exception {
        mockMvc.perform(get("/api/v1/farms")
                        .header("X-Dev-User", "postgres-filter-test-admin")
                        .header("X-Dev-Roles", "SYSTEM_ADMIN")
                        .header("X-Dev-Permissions", "FARM_READ"))
                .andExpect(status().isOk());
    }

    @Test
    void optionalTextFiltersUseTypedEmptySentinelsAcrossPostgresQueries() {
        UUID farmId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        var page = PageRequest.of(0, 10);

        assertThatCode(() -> enterpriseRepository.search(null, "", "", page).getTotalElements())
                .doesNotThrowAnyException();
        assertThatCode(() -> farmRepository.search("", null, null, page).getTotalElements())
                .doesNotThrowAnyException();
        assertThatCode(() -> farmRepository.searchAccessible(
                "postgres-filter-test-user",
                "",
                null,
                null,
                page
        ).getTotalElements()).doesNotThrowAnyException();
        assertThatCode(() -> plotRepository.searchByFarm(
                farmId,
                null,
                null,
                "",
                page
        ).getTotalElements()).doesNotThrowAnyException();
        assertThatCode(() -> areaRepository.searchByFarm(
                farmId,
                null,
                "",
                page
        ).getTotalElements()).doesNotThrowAnyException();
        assertThatCode(() -> soilProfileRepository.searchByPlot(
                farmId,
                plotId,
                null,
                null,
                null,
                "",
                page
        ).getTotalElements()).doesNotThrowAnyException();
        assertThatCode(() -> irrigationZoneRepository.searchByPlot(
                farmId,
                plotId,
                null,
                null,
                "",
                page
        ).getTotalElements()).doesNotThrowAnyException();
    }

    private static boolean isComposePostgresUp() {
        try {
            Class.forName("org.postgresql.Driver");
            try (var connection = DriverManager.getConnection(
                    COMPOSE_JDBC,
                    DATABASE_USER,
                    DATABASE_PASSWORD
            )) {
                return connection.isValid(5);
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean tryStartTestcontainer() {
        try {
            DockerClientFactory.instance().client();
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("agricore_farm")
                    .withUsername(DATABASE_USER)
                    .withPassword(DATABASE_PASSWORD);
            container.start();
            return true;
        } catch (Throwable ignored) {
            container = null;
            return false;
        }
    }
}
