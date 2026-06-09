SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS admin_operation_log (
  id VARCHAR(40) PRIMARY KEY,
  actor_id VARCHAR(32) NULL,
  actor_username VARCHAR(64) NULL,
  actor_role VARCHAR(32) NULL,
  module VARCHAR(32) NOT NULL,
  action VARCHAR(64) NOT NULL,
  target_id VARCHAR(128) NULL,
  target_label VARCHAR(256) NULL,
  result VARCHAR(32) NOT NULL,
  detail VARCHAR(512) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_admin_operation_log_created (created_at),
  INDEX idx_admin_operation_log_module_result (module, result),
  INDEX idx_admin_operation_log_actor (actor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
