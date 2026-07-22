package com.agricore.work.infrastructure.storage;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectStorageConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ObjectStorageConfiguration.class);

    @Test
    void disabledConfigurationProvidesFailClosedStorageWithoutClients() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TaskAttachmentStorage.class);
            assertThat(context).doesNotHaveBean(MinioClient.class);
            assertThat(context.getBean(TaskAttachmentStorage.class))
                    .isInstanceOf(DisabledTaskAttachmentStorage.class);
        });
    }

    @Test
    void enabledConfigurationSeparatesInternalAndPublicClients() {
        contextRunner
                .withPropertyValues(
                        "agricore.object-storage.enabled=true",
                        "agricore.object-storage.endpoint=http://minio:9000",
                        "agricore.object-storage.public-endpoint=https://objects.example",
                        "agricore.object-storage.allowed-hosts=minio,objects.example",
                        "agricore.object-storage.allow-insecure-http=true",
                        "agricore.object-storage.access-key=access-key",
                        "agricore.object-storage.secret-key=secret-key-value"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(TaskAttachmentStorage.class);
                    assertThat(context).hasBean("minioClient");
                    assertThat(context).hasBean("objectStorageDownloadClient");
                    assertThat(context.getBeansOfType(MinioClient.class)).hasSize(2);
                    assertThat(context.getBean(TaskAttachmentStorage.class))
                            .isInstanceOf(MinioTaskAttachmentStorage.class);
                });
    }
}
