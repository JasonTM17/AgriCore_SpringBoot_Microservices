package com.agricore.work.application.service;

import com.agricore.work.domain.exception.WorkException;
import com.agricore.work.infrastructure.storage.ObjectStorageProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Component
final class TaskAttachmentUploadValidator {

    private final TaskAttachmentContentInspector contentInspector;
    private final long maxUploadBytes;

    TaskAttachmentUploadValidator(
            TaskAttachmentContentInspector contentInspector,
            ObjectStorageProperties properties
    ) {
        this.contentInspector = contentInspector;
        this.maxUploadBytes = properties.validatedMaxUploadBytes();
    }

    ValidatedUpload validate(MultipartFile file) {
        validateSize(file);
        TaskAttachmentContentInspector.DetectedContent detected = inspect(file);
        validateDeclaredContentType(file.getContentType(), detected.contentType());
        return new ValidatedUpload(
                normalizedFileName(file.getOriginalFilename(), detected.extension()),
                detected.contentType(),
                detected.extension(),
                sha256(file),
                file.getSize()
        );
    }

    private TaskAttachmentContentInspector.DetectedContent inspect(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return contentInspector.inspect(input);
        } catch (IOException exception) {
            throw readFailed();
        }
    }

    private String sha256(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw readFailed();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void validateSize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new WorkException("ATTACHMENT_EMPTY", "Attachment file must not be empty", 400);
        }
        if (file.getSize() > maxUploadBytes) {
            throw new WorkException("ATTACHMENT_TOO_LARGE", "Attachment exceeds the configured size limit", 413);
        }
    }

    private static void validateDeclaredContentType(String declared, String detected) {
        if (declared == null || declared.isBlank() || "application/octet-stream".equalsIgnoreCase(declared)) {
            return;
        }
        if (!detected.equals(declared.toLowerCase(Locale.ROOT))) {
            throw new WorkException(
                    "ATTACHMENT_CONTENT_TYPE_MISMATCH",
                    "Attachment Content-Type does not match its bytes",
                    415
            );
        }
    }

    private static String normalizedFileName(String original, String extension) {
        String candidate = original == null ? "" : original.trim();
        if (candidate.isBlank()
                || candidate.length() > 255
                || candidate.indexOf('/') >= 0
                || candidate.indexOf('\\') >= 0
                || candidate.chars().anyMatch(Character::isISOControl)) {
            throw invalidName();
        }
        int extensionStart = candidate.lastIndexOf('.');
        String base = extensionStart > 0 ? candidate.substring(0, extensionStart) : candidate;
        if (base.isBlank() || ".".equals(base) || "..".equals(base)) {
            throw invalidName();
        }
        String normalized = base + "." + extension;
        if (normalized.length() > 255) {
            throw invalidName();
        }
        return normalized;
    }

    private static WorkException readFailed() {
        return new WorkException("ATTACHMENT_READ_FAILED", "Attachment content could not be read", 400);
    }

    private static WorkException invalidName() {
        return new WorkException("INVALID_ATTACHMENT_NAME", "Attachment file name is invalid", 400);
    }

    record ValidatedUpload(
            String fileName,
            String contentType,
            String extension,
            String sha256,
            long sizeBytes
    ) {
    }
}
