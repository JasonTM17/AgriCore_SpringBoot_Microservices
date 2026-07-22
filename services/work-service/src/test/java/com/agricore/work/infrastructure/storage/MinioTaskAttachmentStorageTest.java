package com.agricore.work.infrastructure.storage;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinioTaskAttachmentStorageTest {

    @Test
    void createsMissingBucketStoresObjectAndBuildsPrivateDownloadUrl() throws Exception {
        MinioClient client = mock(MinioClient.class);
        MinioClient downloadClient = mock(MinioClient.class);
        when(client.bucketExists(any())).thenReturn(false);
        when(downloadClient.getPresignedObjectUrl(any()))
                .thenReturn("https://objects.example/agricore-work-attachments/tasks/photo.webp?signature=redacted");
        MinioTaskAttachmentStorage storage = new MinioTaskAttachmentStorage(
                client,
                downloadClient,
                configuredProperties()
        );

        storage.store(
                "tasks/00000000-0000-0000-0000-000000000001/photo.webp",
                new ByteArrayInputStream(new byte[]{1, 2, 3}),
                3,
                "image/webp"
        );
        URI url = storage.createDownloadUrl(
                "tasks/00000000-0000-0000-0000-000000000001/photo.webp",
                "field photo.webp"
        );

        verify(client).makeBucket(any());
        verify(client).putObject(any());
        verify(downloadClient).getPresignedObjectUrl(any());
        assertThat(url.getScheme()).isEqualTo("https");
        assertThat(url.getQuery()).contains("signature=redacted");
    }

    @Test
    void rejectsUnsafeObjectKeysBeforeCallingStorage() {
        MinioClient client = mock(MinioClient.class);
        MinioTaskAttachmentStorage storage = new MinioTaskAttachmentStorage(
                client,
                mock(MinioClient.class),
                configuredProperties()
        );

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> storage.remove("../another-tenant/object"));
    }

    private static ObjectStorageProperties configuredProperties() {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setAccessKey("access-key");
        properties.setSecretKey("secret-key-value");
        return properties;
    }
}
