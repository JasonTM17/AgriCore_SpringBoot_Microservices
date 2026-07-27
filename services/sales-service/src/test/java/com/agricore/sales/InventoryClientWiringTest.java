package com.agricore.sales;

import com.agricore.sales.infrastructure.client.InventoryClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Constructs the real {@link InventoryClient}.
 *
 * <p>Every other sales test replaces it with a {@code @MockBean}, so its constructor — and the
 * {@code RestClient.Builder} injection it depends on — was never exercised by the suite. That left
 * a gap where the timeout change could have removed the only {@code RestClient.Builder} bean and
 * broken startup in production while every test stayed green.
 *
 * <p>The builder now comes from Boot's auto-configuration rather than a locally declared bean, with
 * timeouts applied by a {@link RestClientCustomizer}. This asserts both halves of that arrangement
 * still resolve.
 */
@SpringBootTest
@ActiveProfiles("test")
class InventoryClientWiringTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private InventoryClient inventoryClient;

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Autowired
    private List<RestClientCustomizer> customizers;

    @Test
    void realInventoryClientIsConstructable() {
        assertThat(inventoryClient)
                .as("InventoryClient must be constructable, which requires a RestClient.Builder bean")
                .isNotNull();
    }

    @Test
    void restClientBuilderIsAvailableWithoutALocallyDeclaredBean() {
        assertThat(restClientBuilder)
                .as("Boot auto-configuration must still supply RestClient.Builder")
                .isNotNull();
    }

    /**
     * Also pins the reason for using a customizer instead of declaring the builder: Boot
     * contributes its own customizers for message converters and observation instrumentation.
     * A locally declared {@code RestClient.Builder} bean replaces the auto-configured one and
     * silently loses them.
     */
    @Test
    void timeoutCustomizerIsRegisteredAlongsideBootsOwn() {
        List<String> names = customizers.stream()
                .map(c -> c.getClass().getSimpleName())
                .toList();

        assertThat(customizers)
                .as("expected the timeout customizer plus Boot's own, found: %s", names)
                .hasSizeGreaterThanOrEqualTo(2);

        assertThat(context.getBean("inventoryClientTimeouts", RestClientCustomizer.class))
                .as("the customizer carrying the connect/read timeouts must be a bean")
                .isNotNull();
    }
}
