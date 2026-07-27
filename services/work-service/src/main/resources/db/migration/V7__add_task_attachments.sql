CREATE TABLE task_attachments (
    id                  UUID PRIMARY KEY,
    work_task_id        UUID NOT NULL REFERENCES work_tasks(id),
    object_key          VARCHAR(1024) NOT NULL,
    original_file_name  VARCHAR(255) NOT NULL,
    content_type        VARCHAR(64) NOT NULL,
    size_bytes          BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 52428800),
    sha256              CHAR(64) NOT NULL,
    uploaded_by         VARCHAR(255) NOT NULL,
    uploaded_at         TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_task_attachments_object_key
    ON task_attachments (object_key);

CREATE UNIQUE INDEX uk_task_attachments_task_sha256
    ON task_attachments (work_task_id, sha256);

CREATE INDEX idx_task_attachments_task_time
    ON task_attachments (work_task_id, uploaded_at ASC, id ASC);
