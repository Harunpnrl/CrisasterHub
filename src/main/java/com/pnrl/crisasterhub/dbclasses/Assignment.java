package com.pnrl.crisasterhub.dbclasses;

import java.time.LocalDateTime;

public class Assignment {
    private int assignmentId;
    private int requestId;
    private int resourceId;
    private int assignedQuantity;
    private String status;
    private LocalDateTime assignedAt;
    private LocalDateTime updatedAt;

    public Assignment(int assignmentId, int requestId, int resourceId, int assignedQuantity,
                      String status, LocalDateTime assignedAt, LocalDateTime updatedAt) {
        this.assignmentId = assignmentId;
        this.requestId = requestId;
        this.resourceId = resourceId;
        this.assignedQuantity = assignedQuantity;
        this.status = status;
        this.assignedAt = assignedAt;
        this.updatedAt = updatedAt;
    }

    public Assignment() {
    }

    public int getAssignmentId() { return assignmentId; }
    public void setAssignmentId(int assignmentId) { this.assignmentId = assignmentId; }

    public int getRequestId() { return requestId; }
    public void setRequestId(int requestId) { this.requestId = requestId; }

    public int getResourceId() { return resourceId; }
    public void setResourceId(int resourceId) { this.resourceId = resourceId; }

    public int getAssignedQuantity() { return assignedQuantity; }
    public void setAssignedQuantity(int assignedQuantity) { this.assignedQuantity = assignedQuantity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
