CREATE TABLE work_assignments (
    id              UUID PRIMARY KEY,
    work_task_id    UUID NOT NULL REFERENCES work_tasks(id),
    employee_id     UUID NOT NULL,
    assigned_by     VARCHAR(255) NOT NULL,
    assigned_at     TIMESTAMP NOT NULL,
    task_version    BIGINT NOT NULL
);

CREATE UNIQUE INDEX uk_work_assignments_task_version
    ON work_assignments (work_task_id, task_version);

CREATE INDEX idx_work_assignments_task_time
    ON work_assignments (work_task_id, assigned_at DESC, id DESC);

INSERT INTO work_assignments (
    id,
    work_task_id,
    employee_id,
    assigned_by,
    assigned_at,
    task_version
)
SELECT
    id,
    id,
    assigned_employee_id,
    'legacy-migration',
    updated_at,
    version
FROM work_tasks
WHERE assigned_employee_id IS NOT NULL;
