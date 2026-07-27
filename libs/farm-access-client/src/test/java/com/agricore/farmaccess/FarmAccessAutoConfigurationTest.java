package com.agricore.farmaccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FarmAccessAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FarmAccessAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withPropertyValues("agricore.farm-access.base-url=https://farm-service");

    @Test
    void createsClientWithValidatedConfiguration() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(FarmAccessClient.class);
        });
    }

    @Test
    void failsStartupForInvalidTimeout() {
        contextRunner
                .withPropertyValues("agricore.farm-access.connect-timeout=0s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasStackTraceContaining("connect-timeout");
                });
    }

    @Test
    void preservesApplicationProvidedClient() {
        FarmAccessClient customClient = new StubFarmAccessClient();

        contextRunner
                .withBean(FarmAccessClient.class, () -> customClient)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(FarmAccessClient.class)).isSameAs(customClient);
                });
    }

    private static final class StubFarmAccessClient implements FarmAccessClient {

        @Override
        public FarmResourceAccess requireFarm(UUID farmId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FarmResourceAccess requirePlot(UUID plotId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FarmResourceAccess requireFarmPlot(UUID farmId, UUID plotId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSystemAdmin() {
            return false;
        }
    }
}
