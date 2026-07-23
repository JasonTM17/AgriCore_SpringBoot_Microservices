package com.agricore.work.application.service;

import com.agricore.work.domain.exception.WorkException;
import com.agricore.work.infrastructure.persistence.entity.TaskAttachmentEntity;
import com.agricore.work.infrastructure.storage.TaskAttachmentStorage;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskAttachmentServiceTest {

    @Test
    void removesDeterministicStoredObjectWhenMetadataPersistenceFails() throws Exception {
        TaskAttachmentStateService stateService = mock(TaskAttachmentStateService.class);
        TaskAttachmentUploadValidator uploadValidator = mock(TaskAttachmentUploadValidator.class);
        TaskAttachmentStorage storage = mock(TaskAttachmentStorage.class);
        MultipartFile file = mock(MultipartFile.class);
        TaskAttachmentService service = new TaskAttachmentService(stateService, uploadValidator, storage);
        UUID taskId = UUID.randomUUID();
        String sha256 = "a".repeat(64);
        String objectKey = "work-tasks/" + taskId + "/" + sha256 + ".png";

        when(uploadValidator.validate(file)).thenReturn(validatedUpload(sha256));
        when(stateService.validateUpload(taskId, sha256)).thenReturn(Optional.empty());
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[45]));
        when(file.getSize()).thenReturn(45L);
        when(stateService.persist(eq(taskId), any(TaskAttachmentEntity.class)))
                .thenThrow(new WorkException("ATTACHMENT_PERSIST_FAILED", "Metadata could not be saved", 500));

        assertThatThrownBy(() -> service.upload(taskId, file, "worker@example.com"))
                .isInstanceOf(WorkException.class)
                .hasMessage("Metadata could not be saved");

        verify(storage).store(eq(objectKey), any(), eq(45L), eq("image/png"), eq(sha256));
        verify(storage).remove(objectKey);
    }

    @Test
    void returnsExistingAttachmentWithoutWritingDuplicateObject() {
        TaskAttachmentStateService stateService = mock(TaskAttachmentStateService.class);
        TaskAttachmentUploadValidator uploadValidator = mock(TaskAttachmentUploadValidator.class);
        TaskAttachmentStorage storage = mock(TaskAttachmentStorage.class);
        MultipartFile file = mock(MultipartFile.class);
        TaskAttachmentService service = new TaskAttachmentService(stateService, uploadValidator, storage);
        UUID taskId = UUID.randomUUID();
        String sha256 = "b".repeat(64);
        TaskAttachmentEntity existing = existingAttachment(taskId, sha256);

        when(uploadValidator.validate(file)).thenReturn(validatedUpload(sha256));
        when(stateService.validateUpload(taskId, sha256)).thenReturn(Optional.of(existing));

        service.upload(taskId, file, "worker@example.com");

        verify(storage, never()).store(any(), any(), any(Long.class), any(), any());
        verify(stateService, never()).persist(any(), any());
    }

    @Test
    void preservesSharedObjectWhenConcurrentUploadAlreadyPersistedMetadata() throws Exception {
        TaskAttachmentStateService stateService = mock(TaskAttachmentStateService.class);
        TaskAttachmentUploadValidator uploadValidator = mock(TaskAttachmentUploadValidator.class);
        TaskAttachmentStorage storage = mock(TaskAttachmentStorage.class);
        MultipartFile file = mock(MultipartFile.class);
        TaskAttachmentService service = new TaskAttachmentService(stateService, uploadValidator, storage);
        UUID taskId = UUID.randomUUID();
        String sha256 = "c".repeat(64);
        TaskAttachmentEntity winner = existingAttachment(taskId, sha256);

        when(uploadValidator.validate(file)).thenReturn(validatedUpload(sha256));
        when(stateService.validateUpload(taskId, sha256)).thenReturn(Optional.empty());
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[45]));
        when(file.getSize()).thenReturn(45L);
        when(stateService.persist(eq(taskId), any(TaskAttachmentEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate attachment"));
        when(stateService.findExisting(taskId, sha256)).thenReturn(Optional.of(winner));

        service.upload(taskId, file, "worker@example.com");

        verify(storage, never()).remove(any());
    }

    private static TaskAttachmentUploadValidator.ValidatedUpload validatedUpload(String sha256) {
        return new TaskAttachmentUploadValidator.ValidatedUpload(
                "crop-evidence.png",
                "image/png",
                "png",
                sha256,
                45
        );
    }

    private static TaskAttachmentEntity existingAttachment(UUID taskId, String sha256) {
        TaskAttachmentEntity attachment = new TaskAttachmentEntity();
        attachment.setId(UUID.randomUUID());
        attachment.setWorkTaskId(taskId);
        attachment.setObjectKey("work-tasks/" + taskId + "/" + sha256 + ".png");
        attachment.setOriginalFileName("crop-evidence.png");
        attachment.setContentType("image/png");
        attachment.setSizeBytes(45);
        attachment.setSha256(sha256);
        attachment.setUploadedBy("worker@example.com");
        attachment.setUploadedAt(java.time.Instant.now());
        return attachment;
    }
}
