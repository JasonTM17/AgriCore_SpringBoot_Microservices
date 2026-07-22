package com.agricore.work.infrastructure.storage;

import java.io.InputStream;
import java.net.URI;

final class DisabledTaskAttachmentStorage implements TaskAttachmentStorage {

    private static final String MESSAGE = "Task attachment storage is disabled";

    @Override
    public void store(String objectKey, InputStream content, long contentLength, String contentType) {
        throw new AttachmentStorageException(MESSAGE);
    }

    @Override
    public URI createDownloadUrl(String objectKey, String downloadFileName) {
        throw new AttachmentStorageException(MESSAGE);
    }

    @Override
    public void remove(String objectKey) {
        throw new AttachmentStorageException(MESSAGE);
    }
}
