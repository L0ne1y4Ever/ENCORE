SET @sql = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE refund_request ADD COLUMN scope VARCHAR(32) NOT NULL DEFAULT ''ORDER'' AFTER source', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'refund_request' AND column_name = 'scope'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE refund_request ADD COLUMN refund_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 AFTER reviewer_username', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'refund_request' AND column_name = 'refund_amount'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE refund_request ADD COLUMN ticket_count INT NOT NULL DEFAULT 0 AFTER refund_amount', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'refund_request' AND column_name = 'ticket_count'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE refund_request ADD COLUMN requester_id VARCHAR(40) NULL AFTER user_id', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'refund_request' AND column_name = 'requester_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE refund_request
SET
  requester_id = COALESCE(requester_id, user_id),
  scope = COALESCE(scope, 'ORDER')
WHERE requester_id IS NULL OR scope IS NULL;

CREATE TABLE IF NOT EXISTS refund_request_ticket (
  id VARCHAR(40) PRIMARY KEY,
  refund_request_id VARCHAR(40) NOT NULL,
  order_id VARCHAR(40) NOT NULL,
  ticket_id VARCHAR(48) NOT NULL,
  holder_user_id VARCHAR(40) NULL,
  amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_refund_request_ticket (refund_request_id, ticket_id),
  INDEX idx_refund_ticket_request (refund_request_id),
  INDEX idx_refund_ticket_order_ticket (order_id, ticket_id),
  INDEX idx_refund_ticket_holder (holder_user_id, order_id)
);
