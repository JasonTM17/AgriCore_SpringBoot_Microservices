package com.agricore.identity.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "role_permission_policy_audits")
public class RolePermissionPolicyAuditEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private RoleEntity role;

    @Column(name = "policy_version", nullable = false)
    private long policyVersion;

    @Column(name = "actor_subject", nullable = false, length = 200)
    private String actorSubject;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "before_permissions", nullable = false, columnDefinition = "TEXT")
    private String beforePermissions;

    @Column(name = "after_permissions", nullable = false, columnDefinition = "TEXT")
    private String afterPermissions;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public RoleEntity getRole() {
        return role;
    }

    public void setRole(RoleEntity role) {
        this.role = role;
    }

    public long getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(long policyVersion) {
        this.policyVersion = policyVersion;
    }

    public String getActorSubject() {
        return actorSubject;
    }

    public void setActorSubject(String actorSubject) {
        this.actorSubject = actorSubject;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getBeforePermissions() {
        return beforePermissions;
    }

    public void setBeforePermissions(String beforePermissions) {
        this.beforePermissions = beforePermissions;
    }

    public String getAfterPermissions() {
        return afterPermissions;
    }

    public void setAfterPermissions(String afterPermissions) {
        this.afterPermissions = afterPermissions;
    }
}
