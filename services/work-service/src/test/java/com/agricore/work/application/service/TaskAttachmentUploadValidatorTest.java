package com.agricore.work.application.service;

import com.agricore.work.domain.exception.WorkException;
import com.agricore.work.infrastructure.storage.ObjectStorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskAttachmentUploadValidatorTest {

    private final TaskAttachmentUploadValidator validator = new TaskAttachmentUploadValidator(
            new TaskAttachmentContentInspector(),
            new ObjectStorageProperties()
    );

    @Test
    void detectsJpegAndNormalizesUntrustedExtension() {
        var upload = validator.validate(new MockMultipartFile(
                "file",
                "field-photo.tmp",
                "application/octet-stream",
                jpegBytes()
        ));

        assertThat(upload.contentType()).isEqualTo("image/jpeg");
        assertThat(upload.fileName()).isEqualTo("field-photo.jpg");
        assertThat(upload.sha256()).hasSize(64);
    }

    @Test
    void detectsWebpBytes() {
        byte[] webp = webpBytes();
        var upload = validator.validate(new MockMultipartFile("file", "crop.webp", "image/webp", webp));

        assertThat(upload.contentType()).isEqualTo("image/webp");
        assertThat(upload.extension()).isEqualTo("webp");
    }

    @Test
    void rejectsContentTypeMismatchAndPathBearingName() {
        byte[] png = pngBytes();
        assertThatThrownBy(() -> validator.validate(
                new MockMultipartFile("file", "field.png", "image/jpeg", png)
        ))
                .isInstanceOf(WorkException.class)
                .extracting("code")
                .isEqualTo("ATTACHMENT_CONTENT_TYPE_MISMATCH");

        assertThatThrownBy(() -> validator.validate(
                new MockMultipartFile("file", "../field.png", "image/png", png)
        ))
                .isInstanceOf(WorkException.class)
                .extracting("code")
                .isEqualTo("INVALID_ATTACHMENT_NAME");
    }

    @Test
    void rejectsTruncatedImagesThatOnlyHaveARecognizedPrefix() {
        byte[] truncatedPng = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

        assertThatThrownBy(() -> validator.validate(
                new MockMultipartFile("file", "broken.png", "image/png", truncatedPng)
        ))
                .isInstanceOf(WorkException.class)
                .extracting("code")
                .isEqualTo("UNSUPPORTED_ATTACHMENT_CONTENT");
    }

    private static byte[] jpegBytes() {
        byte[] jpeg = new byte[16];
        jpeg[0] = (byte) 0xFF;
        jpeg[1] = (byte) 0xD8;
        jpeg[2] = (byte) 0xFF;
        jpeg[14] = (byte) 0xFF;
        jpeg[15] = (byte) 0xD9;
        return jpeg;
    }

    private static byte[] webpBytes() {
        byte[] webp = new byte[20];
        byte[] prefix = {0x52, 0x49, 0x46, 0x46, 12, 0, 0, 0, 0x57, 0x45, 0x42, 0x50, 0x56, 0x50, 0x38, 0x20};
        System.arraycopy(prefix, 0, webp, 0, prefix.length);
        return webp;
    }

    private static byte[] pngBytes() {
        byte[] png = new byte[45];
        byte[] header = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52};
        byte[] iend = {0, 0, 0, 0, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82};
        System.arraycopy(header, 0, png, 0, header.length);
        System.arraycopy(iend, 0, png, png.length - iend.length, iend.length);
        return png;
    }
}
