SET @sql = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE ticket_order ADD COLUMN order_channel VARCHAR(32) NOT NULL DEFAULT ''ONLINE'' AFTER status', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ticket_order' AND column_name = 'order_channel'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE ticket_order ADD COLUMN payment_method VARCHAR(32) NOT NULL DEFAULT ''SIMULATED'' AFTER order_channel', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ticket_order' AND column_name = 'payment_method'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE ticket_order ADD COLUMN cashier_user_id VARCHAR(32) NULL AFTER payment_method', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ticket_order' AND column_name = 'cashier_user_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE ticket_order ADD INDEX idx_ticket_order_channel_status (order_channel, status)', 'SELECT 1')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'ticket_order' AND index_name = 'idx_ticket_order_channel_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE ticket_order
SET order_channel = 'ONLINE'
WHERE order_channel IS NULL OR order_channel = '';

UPDATE ticket_order
SET payment_method = 'SIMULATED'
WHERE payment_method IS NULL OR payment_method = '';
