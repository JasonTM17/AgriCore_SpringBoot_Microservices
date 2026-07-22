package com.agricore.work.infrastructure.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http.Method;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.http.ContentDisposition;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class MinioTaskAttachmentStorage implements TaskAttachmentStorage {

    private final MinioClient client;
    private final MinioClient downloadClient;
    private final String bucket;
    private final Duration downloadUrlTtl;

    MinioTaskAttachmentStorage(
            MinioClient client,
            MinioClient downloadClient,
            ObjectStorageProperties properties
    ) {
        this.client = client;
        this.downloadClient = downloadClient;
        this.bucket = properties.validatedBucket();
        this.downloadUrlTtl = properties.validatedDownloadUrlTtl();
    }

    @Override
    public void store(String objectKey, InputStream content, long contentLength, String contentType) {
        validateObjectKey(objectKey);
        if (content == null || contentLength <= 0 || contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Attachment content, length, and type are required");
        }
        try {
            ensureBucket();
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(content, contentLength, -1L)
                    .contentType(contentType)
                    .build());
        } catch (Exception exception) {
            throw unavailable("store", exception);
        }
    }

    @Override
    public URI createDownloadUrl(String objectKey, String downloadFileName) {
        validateObjectKey(objectKey);
        String disposition = ContentDisposition.attachment()
                .filename(validatedDownloadFileName(downloadFileName), StandardCharsets.UTF_8)
                .build()
                .toString();
        try {
            String url = downloadClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(Math.toIntExact(downloadUrlTtl.toSeconds()), TimeUnit.SECONDS)
                    .extraQueryParams(Map.of("response-content-disposition", disposition))
                    .build());
            return URI.create(url);
        } catch (Exception exception) {
            throw unavailable("create download URL", exception);
        }
    }

    @Override
    public void remove(String objectKey) {
        validateObjectKey(objectKey);
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            throw unavailable("remove", exception);
        }
    }

    private void ensureBucket() throws Exception {
        BucketExistsArgs existsArgs = BucketExistsArgs.builder().bucket(bucket).build();
        if (client.bucketExists(existsArgs)) {
            return;
        }
        try {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        } catch (Exception exception) {
            if (!client.bucketExists(existsArgs)) {
                throw exception;
            }
        }
    }

    private static void validateObjectKey(String objectKey) {
        if (objectKey == null
                || objectKey.isBlank()
                || objectKey.length() > 1024
                || objectKey.startsWith("/")
                || objectKey.contains("..")
                || objectKey.contains("\\")
                || objectKey.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid attachment object key");
        }
    }

    private static String validatedDownloadFileName(String fileName) {
        if (fileName == null
                || fileName.isBlank()
                || fileName.length() > 255
                || fileName.indexOf('/') >= 0
                || fileName.indexOf('\\') >= 0
                || fileName.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid attachment download file name");
        }
        return fileName;
    }

    private static AttachmentStorageException unavailable(String operation, Exception cause) {
        return new AttachmentStorageException("Object storage could not " + operation + " task attachment", cause);
    }
}
