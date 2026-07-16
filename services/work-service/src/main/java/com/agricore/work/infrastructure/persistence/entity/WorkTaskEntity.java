package com.agricore.work.infrastructure.persistence.entity;

import com.agricore.work.domain.model.TaskStatus;
import com.agricore.work.domain.model.TaskType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "work_tasks")
public class WorkTaskEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 64)
    private String code;
    @Column(name = "crop_cycle_id", nullable = false)
    private UUID cropCycleId;
    @Column(name = "plot_id", nullable = false)
    private UUID plotId;
    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 64)
    private TaskType taskType;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(nullable = false, length = 32)
    private String priority;
    @Column(name = "assigned_employee_id")
    private UUID assignedEmployeeId;
    @Column(name = "scheduled_start")
    private Instant scheduledStart;
    @Column(name = "scheduled_end")
    private Instant scheduledEnd;
    @Column(name = "actual_start")
    private Instant actualStart;
    @Column(name = "actual_end")
    private Instant actualEnd;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskStatus status;
    @Column(columnDefinition = "TEXT")
    private String notes;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public UUID getCropCycleId() { return cropCycleId; }
    public void setCropCycleId(UUID cropCycleId) { this.cropCycleId = cropCycleId; }
    public UUID getPlotId() { return plotId; }
    public void setPlotId(UUID plotId) { this.plotId = plotId; }
    public TaskType getTaskType() { return taskType; }
    public void setTaskType(TaskType taskType) { this.taskType = taskType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public UUID getAssignedEmployeeId() { return assignedEmployeeId; }
    public void setAssignedEmployeeId(UUID assignedEmployeeId) { this.assignedEmployeeId = assignedEmployeeId; }
    public Instant getScheduledStart() { return scheduledStart; }
    public void setScheduledStart(Instant scheduledStart) { this.scheduledStart = scheduledStart; }
    public Instant getScheduledEnd() { return scheduledEnd; }
    public void setScheduledEnd(Instant scheduledEnd) { this.scheduledEnd = scheduledEnd; }
    public Instant getActualStart() { return actualStart; }
    public void setActualStart(Instant actualStart) { this.actualStart = actualStart; }
    public Instant getActualEnd() { return actualEnd; }
    public void setActualEnd(Instant actualEnd) { this.actualEnd = actualEnd; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public long getVersion() { return version; }
}
