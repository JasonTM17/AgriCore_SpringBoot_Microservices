package com.agricore.work.infrastructure.persistence.entity;

import com.agricore.work.domain.model.TaskExecutionAction;
import com.agricore.work.domain.model.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_executions")
public class TaskExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_task_id", nullable = false)
    private UUID workTaskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskExecutionAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 32)
    private TaskStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskStatus status;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "executed_by", nullable = false, length = 255)
    private String executedBy;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @Column(name = "task_version", nullable = false)
    private long taskVersion;

    public Long getId() { return id; }
    public UUID getWorkTaskId() { return workTaskId; }
    public void setWorkTaskId(UUID workTaskId) { this.workTaskId = workTaskId; }
    public TaskExecutionAction getAction() { return action; }
    public void setAction(TaskExecutionAction action) { this.action = action; }
    public TaskStatus getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(TaskStatus previousStatus) { this.previousStatus = previousStatus; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getExecutedBy() { return executedBy; }
    public void setExecutedBy(String executedBy) { this.executedBy = executedBy; }
    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
    public long getTaskVersion() { return taskVersion; }
    public void setTaskVersion(long taskVersion) { this.taskVersion = taskVersion; }
}
