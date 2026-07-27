package com.agricore.work.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ObjectStoragePropertiesTest {

    @Test
    void acceptsLoopbackAndExplicitInternalEndpoints() {
        ObjectStorageProperties loopback = configuredProperties();
        assertThat(loopback.validatedEndpoint()).isEqualTo(URI.create("http://localhost:9000"));

        ObjectStorageProperties internal = configuredProperties();
        internal.setEndpoint("http://minio:9000");
        internal.setPublicEndpoint("https://objects.example");
        internal.setAllowedHosts(Set.of("minio", "objects.example"));
        internal.setAllowInsecureHttp(true);
        assertThat(internal.validatedEndpoint()).isEqualTo(URI.create("http://minio:9000"));
        assertThat(internal.validatedPublicEndpoint()).isEqualTo(URI.create("https://objects.example"));
    }

    @Test
    void rejectsUnapprovedCredentialBearingOrPathEndpoints() {
        assertInvalidEndpoint("https://attacker.example");
        assertInvalidEndpoint("https://user:secret@minio");
        assertInvalidEndpoint("https://minio/private");
        assertInvalidEndpoint("https://minio?redirect=attacker.example");

        ObjectStorageProperties properties = configuredProperties();
        properties.setPublicEndpoint("https://attacker.example");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(properties::validatedPublicEndpoint);
    }

    @Test
    void validatesCredentialsBucketAndDownloadTtlWithoutExposingSecrets() {
        ObjectStorageProperties properties = configuredProperties();
        assertThat(properties.validatedAccessKey()).isEqualTo("access-key");
        assertThat(properties.validatedSecretKey()).isEqualTo("secret-key-value");
        assertThat(properties.validatedBucket()).isEqualTo("agricore-work-attachments");

        properties.setBucket("192.168.1.1");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(properties::validatedBucket);

        properties.setDownloadUrlTtl(Duration.ofHours(25));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(properties::validatedDownloadUrlTtl);
    }

    @Test
    void rejectsUnsafeUploadAndAttachmentLimits() {
        ObjectStorageProperties properties = configuredProperties();
        properties.setMaxUploadSize(DataSize.ofBytes(1023));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(properties::validatedMaxUploadBytes);

        properties.setMaxUploadSize(DataSize.ofMegabytes(51));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(properties::validatedMaxUploadBytes);

        properties.setMaxAttachmentsPerTask(101);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(properties::validatedMaxAttachmentsPerTask);
    }

    private static ObjectStorageProperties configuredProperties() {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setAccessKey("access-key");
        properties.setSecretKey("secret-key-value");
        return properties;
    }

    private static void assertInvalidEndpoint(String endpoint) {
        ObjectStorageProperties properties = configuredProperties();
        properties.setEndpoint(endpoint);
        properties.setAllowedHosts(Set.of("localhost", "minio"));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(properties::validatedEndpoint);
    }
}
