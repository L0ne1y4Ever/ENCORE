CREATE TABLE IF NOT EXISTS refund_request (
  id VARCHAR(40) PRIMARY KEY,
  order_id VARCHAR(40) NOT NULL,
  user_id VARCHAR(40) NOT NULL,
  status VARCHAR(32) NOT NULL,
  source VARCHAR(32) NOT NULL,
  reason VARCHAR(500) NULL,
  review_note VARCHAR(500) NULL,
  reviewer_id VARCHAR(40) NULL,
  reviewer_username VARCHAR(100) NULL,
  requested_at DATETIME NOT NULL,
  reviewed_at DATETIME NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_refund_request_order_status (order_id, status),
  INDEX idx_refund_request_user_status (user_id, status)
);
