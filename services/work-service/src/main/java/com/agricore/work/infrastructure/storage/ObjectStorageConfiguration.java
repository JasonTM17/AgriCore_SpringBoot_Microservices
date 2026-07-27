package com.agricore.work.infrastructure.storage;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class ObjectStorageConfiguration {

    @Bean
    @ConditionalOnProperty(name = "agricore.object-storage.enabled", havingValue = "true")
    MinioClient minioClient(ObjectStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.validatedEndpoint().toString())
                .credentials(properties.validatedAccessKey(), properties.validatedSecretKey())
                .build();
    }

    @Bean("objectStorageDownloadClient")
    @ConditionalOnProperty(name = "agricore.object-storage.enabled", havingValue = "true")
    MinioClient objectStorageDownloadClient(ObjectStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.validatedPublicEndpoint().toString())
                .credentials(properties.validatedAccessKey(), properties.validatedSecretKey())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "agricore.object-storage.enabled", havingValue = "true")
    TaskAttachmentStorage minioTaskAttachmentStorage(
            @Qualifier("minioClient") MinioClient minioClient,
            @Qualifier("objectStorageDownloadClient") MinioClient downloadClient,
            ObjectStorageProperties properties
    ) {
        return new MinioTaskAttachmentStorage(minioClient, downloadClient, properties);
    }

    @Bean
    @ConditionalOnProperty(
            name = "agricore.object-storage.enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    TaskAttachmentStorage disabledTaskAttachmentStorage() {
        return new DisabledTaskAttachmentStorage();
    }
}
