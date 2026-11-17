-- Run this migration with the deployment database user before starting the app.
CREATE TABLE IF NOT EXISTS OPERATOR_AUDIT_LOG (
    audit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator_user_id INT NOT NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id INT NOT NULL,
    details VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
