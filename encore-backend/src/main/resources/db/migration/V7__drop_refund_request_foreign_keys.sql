SET @sql = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE refund_request DROP FOREIGN KEY fk_refund_request_order', 'SELECT 1')
  FROM information_schema.table_constraints
  WHERE table_schema = DATABASE()
    AND table_name = 'refund_request'
    AND constraint_name = 'fk_refund_request_order'
    AND constraint_type = 'FOREIGN KEY'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) > 0, 'ALTER TABLE refund_request DROP FOREIGN KEY fk_refund_request_user', 'SELECT 1')
  FROM information_schema.table_constraints
  WHERE table_schema = DATABASE()
    AND table_name = 'refund_request'
    AND constraint_name = 'fk_refund_request_user'
    AND constraint_type = 'FOREIGN KEY'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
