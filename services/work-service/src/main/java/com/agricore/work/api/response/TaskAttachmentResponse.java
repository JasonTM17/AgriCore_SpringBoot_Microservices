package com.agricore.work.api.response;

import java.time.Instant;
import java.util.UUID;

public record TaskAttachmentResponse(
        UUID id,
        UUID workTaskId,
        String fileName,
        String contentType,
        long sizeBytes,
        String sha256,
        String uploadedBy,
        Instant uploadedAt,
        String downloadPath
) {
}
