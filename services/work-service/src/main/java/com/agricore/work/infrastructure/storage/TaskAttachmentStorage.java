package com.agricore.work.infrastructure.storage;

import java.io.InputStream;
import java.net.URI;

public interface TaskAttachmentStorage {

    void store(
            String objectKey,
            InputStream content,
            long contentLength,
            String contentType,
            String sha256
    );

    URI createDownloadUrl(String objectKey, String downloadFileName);

    void remove(String objectKey);
}
