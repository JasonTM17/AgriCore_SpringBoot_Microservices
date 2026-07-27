package com.agricore.work.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "work_assignments")
public class WorkAssignmentEntity {

    @Id
    private UUID id;

    @Column(name = "work_task_id", nullable = false)
    private UUID workTaskId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "assigned_by", nullable = false, length = 255)
    private String assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "task_version", nullable = false)
    private long taskVersion;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkTaskId() { return workTaskId; }
    public void setWorkTaskId(UUID workTaskId) { this.workTaskId = workTaskId; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }
    public String getAssignedBy() { return assignedBy; }
    public void setAssignedBy(String assignedBy) { this.assignedBy = assignedBy; }
    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }
    public long getTaskVersion() { return taskVersion; }
    public void setTaskVersion(long taskVersion) { this.taskVersion = taskVersion; }
}
