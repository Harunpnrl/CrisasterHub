package com.pnrl.crisasterhub.dbclasses;

import java.time.LocalDateTime;

public class Resource {
    private int resourceId;
    private int userId;
    private int locationId;
    private String category;
    private int quantity;
    private String description;
    private LocalDateTime createdAt;

    public Resource(int resourceId, int userId, int locationId, String category,
                    int quantity, String description, LocalDateTime createdAt) {
        this.resourceId = resourceId;
        this.userId = userId;
        this.locationId = locationId;
        this.category = category;
        this.quantity = quantity;
        this.description = description;
        this.createdAt = createdAt;
    }

    public Resource(String category, int quantity, String description) {
        this.category = category;
        this.quantity = quantity;
        this.description = description;
    }

    public Resource() {
    }

    public int getResourceId() { return resourceId; }
    public void setResourceId(int resourceId) { this.resourceId = resourceId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getLocationId() { return locationId; }
    public void setLocationId(int locationId) { this.locationId = locationId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}