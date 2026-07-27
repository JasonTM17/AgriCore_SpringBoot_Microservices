package com.agricore.work.application.service;

import com.agricore.work.api.response.TaskAttachmentResponse;
import com.agricore.work.infrastructure.persistence.entity.TaskAttachmentEntity;

final class TaskAttachmentResponses {

    private TaskAttachmentResponses() {
    }

    static TaskAttachmentResponse from(TaskAttachmentEntity attachment) {
        return new TaskAttachmentResponse(
                attachment.getId(),
                attachment.getWorkTaskId(),
                attachment.getOriginalFileName(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getSha256(),
                attachment.getUploadedBy(),
                attachment.getUploadedAt(),
                "/api/v1/work-tasks/" + attachment.getWorkTaskId()
                        + "/attachments/" + attachment.getId() + "/download"
        );
    }
}
