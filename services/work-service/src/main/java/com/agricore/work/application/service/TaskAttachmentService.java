package com.agricore.work.application.service;

import com.agricore.work.api.response.TaskAttachmentResponse;
import com.agricore.work.domain.exception.WorkException;
import com.agricore.work.infrastructure.persistence.entity.TaskAttachmentEntity;
import com.agricore.work.infrastructure.storage.AttachmentStorageException;
import com.agricore.work.infrastructure.storage.TaskAttachmentStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TaskAttachmentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskAttachmentService.class);

    private final TaskAttachmentStateService stateService;
    private final TaskAttachmentUploadValidator uploadValidator;
    private final TaskAttachmentStorage storage;

    public TaskAttachmentService(
            TaskAttachmentStateService stateService,
            TaskAttachmentUploadValidator uploadValidator,
            TaskAttachmentStorage storage
    ) {
        this.stateService = stateService;
        this.uploadValidator = uploadValidator;
        this.storage = storage;
    }

    public TaskAttachmentResponse upload(UUID taskId, MultipartFile file, String uploadedBy) {
        String actor = AuthenticatedActor.requireValid(uploadedBy);
        TaskAttachmentUploadValidator.ValidatedUpload upload = uploadValidator.validate(file);
        var existing = stateService.validateUpload(taskId, upload.sha256());
        if (existing.isPresent()) {
            return TaskAttachmentResponses.from(existing.get());
        }

        UUID attachmentId = UUID.randomUUID();
        String objectKey = "work-tasks/" + taskId + "/" + upload.sha256() + "." + upload.extension();
        store(file, objectKey, upload.contentType(), upload.sha256());

        TaskAttachmentEntity candidate = candidate(
                attachmentId,
                taskId,
                objectKey,
                upload.fileName(),
                upload.contentType(),
                upload.sizeBytes(),
                upload.sha256(),
                actor
        );
        try {
            return TaskAttachmentResponses.from(stateService.persist(taskId, candidate));
        } catch (DataIntegrityViolationException exception) {
            var concurrentWinner = stateService.findExisting(taskId, upload.sha256());
            if (concurrentWinner.isPresent()) {
                return TaskAttachmentResponses.from(concurrentWinner.get());
            }
            compensate(objectKey);
            throw exception;
        } catch (RuntimeException exception) {
            compensate(objectKey);
            throw exception;
        }
    }

    public List<TaskAttachmentResponse> list(UUID taskId) {
        return stateService.list(taskId).stream().map(TaskAttachmentResponses::from).toList();
    }

    public URI createDownloadUrl(UUID taskId, UUID attachmentId) {
        TaskAttachmentEntity attachment = stateService.get(taskId, attachmentId);
        try {
            return storage.createDownloadUrl(attachment.getObjectKey(), attachment.getOriginalFileName());
        } catch (AttachmentStorageException exception) {
            throw storageUnavailable();
        }
    }

    private void store(MultipartFile file, String objectKey, String contentType, String sha256) {
        try (InputStream input = file.getInputStream()) {
            storage.store(objectKey, input, file.getSize(), contentType, sha256);
        } catch (IOException exception) {
            throw new WorkException("ATTACHMENT_READ_FAILED", "Attachment content could not be read", 400);
        } catch (AttachmentStorageException exception) {
            throw storageUnavailable();
        }
    }

    private void compensate(String objectKey) {
        try {
            storage.remove(objectKey);
        } catch (AttachmentStorageException exception) {
            LOGGER.warn("Failed to remove orphaned task attachment object {}", objectKey, exception);
        }
    }

    private static TaskAttachmentEntity candidate(
            UUID id,
            UUID taskId,
            String objectKey,
            String fileName,
            String contentType,
            long sizeBytes,
            String sha256,
            String actor
    ) {
        TaskAttachmentEntity attachment = new TaskAttachmentEntity();
        attachment.setId(id);
        attachment.setWorkTaskId(taskId);
        attachment.setObjectKey(objectKey);
        attachment.setOriginalFileName(fileName);
        attachment.setContentType(contentType);
        attachment.setSizeBytes(sizeBytes);
        attachment.setSha256(sha256);
        attachment.setUploadedBy(actor);
        attachment.setUploadedAt(Instant.now());
        return attachment;
    }

    private static WorkException storageUnavailable() {
        return new WorkException(
                "ATTACHMENT_STORAGE_UNAVAILABLE",
                "Task attachment storage is temporarily unavailable",
                503
        );
    }
}
