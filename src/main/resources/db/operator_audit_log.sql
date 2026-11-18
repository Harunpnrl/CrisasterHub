-- CrisasterHub database bootstrap/migration.
-- Run this file once with the deployment database user before starting the app.
CREATE DATABASE IF NOT EXISTS crisis_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE crisis_db;

CREATE TABLE IF NOT EXISTS `USER` (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(100) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS LOCATION (
    location_id INT AUTO_INCREMENT PRIMARY KEY,
    city VARCHAR(100) NOT NULL,
    district VARCHAR(100) NOT NULL,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8)
);

CREATE TABLE IF NOT EXISTS AID_REQUEST (
    request_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    location_id INT NOT NULL,
    category VARCHAR(40) NOT NULL,
    urgency VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'Pending',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_request_user FOREIGN KEY (user_id) REFERENCES `USER` (user_id),
    CONSTRAINT fk_request_location FOREIGN KEY (location_id) REFERENCES LOCATION (location_id),
    INDEX idx_request_status (status),
    INDEX idx_request_category (category)
);

CREATE TABLE IF NOT EXISTS RESOURCE (
    resource_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    location_id INT NOT NULL,
    category VARCHAR(40) NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    description TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_resource_quantity CHECK (quantity >= 0),
    CONSTRAINT fk_resource_user FOREIGN KEY (user_id) REFERENCES `USER` (user_id),
    CONSTRAINT fk_resource_location FOREIGN KEY (location_id) REFERENCES LOCATION (location_id),
    INDEX idx_resource_user_category (user_id, category)
);

CREATE TABLE IF NOT EXISTS ASSIGNMENT (
    assignment_id INT AUTO_INCREMENT PRIMARY KEY,
    request_id INT NOT NULL,
    resource_id INT NOT NULL,
    assigned_quantity INT NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL DEFAULT 'Assigned',
    assigned_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_assignment_quantity CHECK (assigned_quantity > 0),
    CONSTRAINT fk_assignment_request FOREIGN KEY (request_id) REFERENCES AID_REQUEST (request_id),
    CONSTRAINT fk_assignment_resource FOREIGN KEY (resource_id) REFERENCES RESOURCE (resource_id),
    INDEX idx_assignment_request_status (request_id, status),
    INDEX idx_assignment_resource_status (resource_id, status)
);

CREATE TABLE IF NOT EXISTS OPERATOR_AUDIT_LOG (
    audit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator_user_id INT NOT NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id INT NOT NULL,
    details VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_operator FOREIGN KEY (operator_user_id) REFERENCES `USER` (user_id),
    INDEX idx_audit_created_at (created_at)
);

-- Normalize installations created from the earlier ENUM-based schema.
ALTER TABLE AID_REQUEST MODIFY category VARCHAR(40) NOT NULL;
ALTER TABLE AID_REQUEST MODIFY urgency VARCHAR(20) NOT NULL DEFAULT 'MEDIUM';
ALTER TABLE AID_REQUEST MODIFY status VARCHAR(30) NOT NULL DEFAULT 'Pending';
ALTER TABLE RESOURCE MODIFY category VARCHAR(40) NOT NULL;
ALTER TABLE ASSIGNMENT MODIFY status VARCHAR(30) NOT NULL DEFAULT 'Assigned';
