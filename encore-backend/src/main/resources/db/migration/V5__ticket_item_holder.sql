SET @sql = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE ticket_item ADD COLUMN holder_user_id VARCHAR(32) NULL AFTER area_inventory_id', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ticket_item' AND column_name = 'holder_user_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE ticket_item ADD COLUMN holder_display_name VARCHAR(64) NULL AFTER holder_user_id', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ticket_item' AND column_name = 'holder_display_name'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE ticket_item ti
JOIN ticket_order tor ON tor.id = ti.order_id
LEFT JOIN user_account ua ON ua.id = tor.user_id
SET
  ti.holder_user_id = COALESCE(ti.holder_user_id, tor.user_id),
  ti.holder_display_name = COALESCE(ti.holder_display_name, ua.display_name, ua.username, tor.user_id)
WHERE ti.holder_user_id IS NULL;

SET @sql = (
  SELECT IF(COUNT(*) = 0, 'CREATE INDEX idx_ticket_item_holder_order ON ticket_item (holder_user_id, order_id)', 'SELECT 1')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'ticket_item' AND index_name = 'idx_ticket_item_holder_order'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
