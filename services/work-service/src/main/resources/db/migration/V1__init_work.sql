CREATE TABLE work_tasks (
    id                      UUID PRIMARY KEY,
    code                    VARCHAR(64) NOT NULL,
    crop_cycle_id           UUID NOT NULL,
    plot_id                 UUID NOT NULL,
    task_type               VARCHAR(64) NOT NULL,
    title                   VARCHAR(200) NOT NULL,
    description             TEXT,
    priority                VARCHAR(32) NOT NULL,
    assigned_employee_id    UUID,
    scheduled_start         TIMESTAMP,
    scheduled_end           TIMESTAMP,
    actual_start            TIMESTAMP,
    actual_end              TIMESTAMP,
    status                  VARCHAR(32) NOT NULL,
    notes                   TEXT,
    created_at              TIMESTAMP NOT NULL,
    updated_at              TIMESTAMP NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_work_tasks_code ON work_tasks (code);
CREATE INDEX idx_work_tasks_cycle ON work_tasks (crop_cycle_id);
CREATE INDEX idx_work_tasks_status ON work_tasks (status);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(150) NOT NULL,
    topic VARCHAR(200) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    publish_attempts INT NOT NULL DEFAULT 0,
    last_error TEXT
);
