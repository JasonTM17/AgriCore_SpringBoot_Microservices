package com.agricore.assistant;

import com.agricore.farmaccess.FarmAccessProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantFarmAccessConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(FarmAccessPropertiesConfiguration.class)
            .withPropertyValues(
                    "FARM_SERVICE_URL=http://farm-service:8082",
                    "FARM_SERVICE_ALLOWED_HOSTS=farm-service",
                    "FARM_SERVICE_ALLOW_INSECURE_HTTP=true"
            );

    @Test
    void bindsComposeFarmAccessEnvironmentThroughApplicationConfiguration() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            FarmAccessProperties properties = context.getBean(FarmAccessProperties.class);
            assertThat(properties.getBaseUrl()).isEqualTo("http://farm-service:8082");
            assertThat(properties.getAllowedHosts()).containsExactly("farm-service");
            assertThat(properties.isAllowInsecureHttp()).isTrue();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FarmAccessProperties.class)
    static class FarmAccessPropertiesConfiguration {
    }
}
