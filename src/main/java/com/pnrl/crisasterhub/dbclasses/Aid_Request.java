package com.pnrl.crisasterhub.dbclasses;

import java.time.LocalDateTime;

public class Aid_Request {
    private int requestId;
    private int userId;
    private int locationId;
    private String category;
    private String urgency;
    private String description;
    private String status;
    private LocalDateTime createdAt;
    private String locationName;

    public Aid_Request(int requestId, int userId, int locationId, String locationName, String category,
                       String urgency, String description, String status, LocalDateTime createdAt) {
        this.requestId = requestId;
        this.userId = userId;
        this.locationId = locationId;
        this.locationName = locationName;
        this.category = category;
        this.urgency = urgency;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Aid_Request(int requestId, String category, String urgency, String description) {
        this.requestId = requestId;
        this.category = category;
        this.urgency = urgency;
        this.description = description;
    }

    public Aid_Request() {
    }

    public int getRequestId() { return requestId; }
    public void setRequestId(int requestId) { this.requestId = requestId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getLocationId() { return locationId; }
    public void setLocationId(int locationId) { this.locationId = locationId; }

    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}