SET NAMES utf8mb4;

SET @sql = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE encore_show ADD COLUMN intro TEXT NULL AFTER description', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'encore_show' AND column_name = 'intro'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE encore_show ADD COLUMN cast_members TEXT NULL AFTER intro', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'encore_show' AND column_name = 'cast_members'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE encore_show ADD COLUMN creative_team TEXT NULL AFTER cast_members', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'encore_show' AND column_name = 'creative_team'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE encore_show ADD COLUMN full_synopsis TEXT NULL AFTER creative_team', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'encore_show' AND column_name = 'full_synopsis'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS schedule_seat (
  id VARCHAR(64) PRIMARY KEY,
  schedule_id VARCHAR(32) NOT NULL,
  seat_code VARCHAR(32) NOT NULL,
  row_no INT NOT NULL,
  col_no INT NOT NULL,
  section VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
  price DECIMAL(10, 2) NOT NULL,
  area_id VARCHAR(32) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_schedule_seat_code (schedule_id, seat_code),
  INDEX idx_schedule_seat_schedule_status (schedule_id, status),
  CONSTRAINT fk_schedule_seat_schedule
    FOREIGN KEY (schedule_id) REFERENCES show_schedule (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @sql = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE schedule_seat ADD COLUMN area_id VARCHAR(32) NULL AFTER price', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'schedule_seat' AND column_name = 'area_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS ticket_order (
  id VARCHAR(40) PRIMARY KEY,
  user_id VARCHAR(32) NOT NULL,
  schedule_id VARCHAR(32) NOT NULL,
  total_amount DECIMAL(10, 2) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at DATETIME NOT NULL,
  paid_at DATETIME NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_ticket_order_user_status (user_id, status),
  INDEX idx_ticket_order_schedule_status (schedule_id, status),
  CONSTRAINT fk_ticket_order_user
    FOREIGN KEY (user_id) REFERENCES user_account (id),
  CONSTRAINT fk_ticket_order_schedule
    FOREIGN KEY (schedule_id) REFERENCES show_schedule (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS ticket_item (
  id VARCHAR(48) PRIMARY KEY,
  order_id VARCHAR(40) NOT NULL,
  schedule_id VARCHAR(32) NOT NULL,
  seat_id VARCHAR(32) NULL,
  ticket_code VARCHAR(64) NOT NULL UNIQUE,
  status VARCHAR(32) NOT NULL DEFAULT 'UNUSED',
  area_inventory_id VARCHAR(32) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_ticket_item_order (order_id),
  INDEX idx_ticket_item_code_status (ticket_code, status),
  CONSTRAINT fk_ticket_item_order
    FOREIGN KEY (order_id) REFERENCES ticket_order (id)
    ON DELETE CASCADE,
  CONSTRAINT fk_ticket_item_schedule_seat
    FOREIGN KEY (schedule_id, seat_id) REFERENCES schedule_seat (schedule_id, seat_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @sql = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE ticket_item ADD COLUMN area_inventory_id VARCHAR(32) NULL AFTER status', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ticket_item' AND column_name = 'area_inventory_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS venue_area (
  id VARCHAR(32) PRIMARY KEY,
  hall_id VARCHAR(64) NOT NULL,
  name VARCHAR(64) NOT NULL,
  code VARCHAR(32) NOT NULL,
  area_type VARCHAR(32) NOT NULL,
  is_seated BOOLEAN NOT NULL DEFAULT FALSE,
  capacity INT NOT NULL,
  base_price DECIMAL(10, 2) NOT NULL,
  available_count INT NOT NULL DEFAULT 0,
  locked_count INT NOT NULL DEFAULT 0,
  sold_count INT NOT NULL DEFAULT 0,
  position_data TEXT NULL,
  color VARCHAR(32) NULL,
  description VARCHAR(256) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS schedule_area_inventory (
  id VARCHAR(32) PRIMARY KEY,
  schedule_id VARCHAR(32) NOT NULL,
  area_id VARCHAR(32) NOT NULL,
  price DECIMAL(10, 2) NOT NULL,
  total_count INT NOT NULL,
  available_count INT NOT NULL,
  locked_count INT NOT NULL,
  sold_count INT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_schedule_area (schedule_id, area_id),
  CONSTRAINT fk_inventory_schedule
    FOREIGN KEY (schedule_id) REFERENCES show_schedule (id)
    ON DELETE CASCADE,
  CONSTRAINT fk_inventory_area
    FOREIGN KEY (area_id) REFERENCES venue_area (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS venue (
  id VARCHAR(32) PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  city VARCHAR(64) NULL,
  address VARCHAR(256) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_venue_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS venue_hall (
  id VARCHAR(32) PRIMARY KEY,
  venue_id VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  hall_type VARCHAR(32) NOT NULL DEFAULT 'THEATER',
  capacity INT NOT NULL DEFAULT 0,
  clearance_minutes INT NOT NULL DEFAULT 30,
  default_layout_id VARCHAR(32) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_venue_hall_venue (venue_id),
  CONSTRAINT fk_venue_hall_venue
    FOREIGN KEY (venue_id) REFERENCES venue (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS seat_layout (
  id VARCHAR(32) PRIMARY KEY,
  hall_id VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  ticket_mode VARCHAR(32) NOT NULL DEFAULT 'SEATED',
  version INT NOT NULL DEFAULT 1,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_seat_layout_hall_status (hall_id, status),
  CONSTRAINT fk_seat_layout_hall
    FOREIGN KEY (hall_id) REFERENCES venue_hall (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS seat_layout_area (
  id VARCHAR(32) PRIMARY KEY,
  layout_id VARCHAR(32) NOT NULL,
  name VARCHAR(64) NOT NULL,
  code VARCHAR(32) NOT NULL,
  area_type VARCHAR(32) NOT NULL,
  is_seated BOOLEAN NOT NULL DEFAULT FALSE,
  capacity INT NOT NULL,
  base_price DECIMAL(10, 2) NOT NULL,
  color VARCHAR(32) NULL,
  description VARCHAR(256) NULL,
  position_data TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_seat_layout_area_layout (layout_id),
  CONSTRAINT fk_seat_layout_area_layout
    FOREIGN KEY (layout_id) REFERENCES seat_layout (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS seat_layout_seat (
  id VARCHAR(64) PRIMARY KEY,
  layout_id VARCHAR(32) NOT NULL,
  area_id VARCHAR(32) NULL,
  seat_code VARCHAR(32) NOT NULL,
  row_no INT NOT NULL,
  col_no INT NOT NULL,
  section VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
  price DECIMAL(10, 2) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_layout_seat_code (layout_id, seat_code),
  INDEX idx_seat_layout_seat_layout_area (layout_id, area_id),
  CONSTRAINT fk_seat_layout_seat_layout
    FOREIGN KEY (layout_id) REFERENCES seat_layout (id)
    ON DELETE CASCADE,
  CONSTRAINT fk_seat_layout_seat_area
    FOREIGN KEY (area_id) REFERENCES seat_layout_area (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @sql = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE show_schedule ADD COLUMN hall_id VARCHAR(32) NULL AFTER show_id', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'show_schedule' AND column_name = 'hall_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE show_schedule ADD COLUMN layout_id VARCHAR(32) NULL AFTER hall_id', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'show_schedule' AND column_name = 'layout_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE show_schedule ADD COLUMN business_date DATE NULL AFTER theater_name', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'show_schedule' AND column_name = 'business_date'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE show_schedule ADD COLUMN sale_start_time DATETIME NULL AFTER end_time', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'show_schedule' AND column_name = 'sale_start_time'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE show_schedule ADD COLUMN sale_end_time DATETIME NULL AFTER sale_start_time', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'show_schedule' AND column_name = 'sale_end_time'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE show_schedule ADD COLUMN publish_status VARCHAR(32) NOT NULL DEFAULT ''PUBLISHED'' AFTER status', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'show_schedule' AND column_name = 'publish_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO venue (id, name, city, address, status)
VALUES
  ('ven-theater', 'ENCORE 剧院中心', '上海', '人民大道 100 号', 'ACTIVE'),
  ('ven-stadium', 'ENCORE 体育中心', '上海', '星河路 88 号', 'ACTIVE'),
  ('ven-cinema', 'ENCORE 影城', '上海', '光影广场 6 层', 'ACTIVE');

INSERT IGNORE INTO venue_hall (id, venue_id, name, hall_type, capacity, clearance_minutes, default_layout_id, status)
VALUES
  ('hall-main', 'ven-theater', 'Main Hall', 'THEATER', 150, 30, 'lay-main-seated', 'ACTIVE'),
  ('hall-opera', 'ven-theater', 'Opera House', 'THEATER', 150, 30, 'lay-opera-seated', 'ACTIVE'),
  ('hall-stadium', 'ven-stadium', 'Grand Stadium', 'STADIUM', 30000, 60, NULL, 'ACTIVE'),
  ('hall-imax', 'ven-cinema', 'IMAX Cinema', 'CINEMA', 96, 20, 'lay-imax-seated', 'ACTIVE'),
  ('hall-galaxy', 'ven-stadium', '星河体育场', 'STADIUM', 1380, 60, 'lay-galaxy-mixed', 'ACTIVE');

UPDATE venue_hall
SET default_layout_id = CASE id
  WHEN 'hall-main' THEN 'lay-main-seated'
  WHEN 'hall-opera' THEN 'lay-opera-seated'
  WHEN 'hall-imax' THEN 'lay-imax-seated'
  WHEN 'hall-galaxy' THEN 'lay-galaxy-mixed'
  ELSE default_layout_id
END
WHERE id IN ('hall-main', 'hall-opera', 'hall-imax', 'hall-galaxy');

INSERT IGNORE INTO seat_layout (id, hall_id, name, ticket_mode, version, status)
VALUES
  ('lay-main-seated', 'hall-main', 'Main Hall 标准座位布局', 'SEATED', 1, 'PUBLISHED'),
  ('lay-opera-seated', 'hall-opera', 'Opera House 芭蕾座位布局', 'SEATED', 1, 'PUBLISHED'),
  ('lay-imax-seated', 'hall-imax', 'IMAX Cinema 标准影厅布局', 'SEATED', 1, 'PUBLISHED'),
  ('lay-galaxy-mixed', 'hall-galaxy', '星河体育场混合票布局', 'MIXED', 1, 'PUBLISHED');

INSERT IGNORE INTO seat_layout_seat (id, layout_id, area_id, seat_code, row_no, col_no, section, status, price)
SELECT
  CONCAT(layouts.layout_id, ':seat-', r.row_no, '-', c.col_no),
  layouts.layout_id,
  NULL,
  CONCAT('seat-', r.row_no, '-', c.col_no),
  r.row_no,
  c.col_no,
  CASE WHEN r.row_no <= 3 THEN 'VIP' WHEN r.row_no <= 7 THEN 'A' ELSE 'B' END,
  CASE WHEN c.col_no = 8 AND r.row_no IN (1, 5, 9) THEN 'DISABLED' ELSE 'AVAILABLE' END,
  CASE WHEN r.row_no <= 3 THEN layouts.vip_price WHEN r.row_no <= 7 THEN layouts.standard_price ELSE layouts.economy_price END
FROM (
  SELECT 'lay-main-seated' AS layout_id, 10 AS rows_count, 15 AS cols_count, 150.00 AS vip_price, 100.00 AS standard_price, 50.00 AS economy_price
  UNION ALL SELECT 'lay-opera-seated', 10, 15, 200.00, 120.00, 80.00
  UNION ALL SELECT 'lay-imax-seated', 8, 12, 30.00, 25.00, 20.00
) layouts
CROSS JOIN (
  SELECT 1 AS row_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
  UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
) r
CROSS JOIN (
  SELECT 1 AS col_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
  UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
  UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
) c
WHERE r.row_no <= layouts.rows_count AND c.col_no <= layouts.cols_count;

INSERT IGNORE INTO seat_layout_area (id, layout_id, name, code, area_type, is_seated, capacity, base_price, color, description)
VALUES
  ('area-lay-galaxy-vip-a', 'lay-galaxy-mixed', 'VIP A区', 'VIP_A', 'VIP', FALSE, 80, 1680.00, '#c8955a', '舞台正前超近距离站席，尽享震撼音效'),
  ('area-lay-galaxy-infield-a', 'lay-galaxy-mixed', '内场A区', 'INFIELD_A', 'FIELD', FALSE, 300, 1280.00, '#4a90e2', '内场前排站立区，现场气氛极佳'),
  ('area-lay-galaxy-infield-b', 'lay-galaxy-mixed', '内场B区', 'INFIELD_B', 'FIELD', FALSE, 500, 980.00, '#50e3c2', '内场后排站立区，性价比极高'),
  ('area-lay-galaxy-stand-1', 'lay-galaxy-mixed', '看台一区', 'STAND_1', 'BALCONY', TRUE, 200, 680.00, '#f5a623', '一侧看台固定座椅，绝佳全景视野'),
  ('area-lay-galaxy-stand-2', 'lay-galaxy-mixed', '看台二区', 'STAND_2', 'BALCONY', TRUE, 200, 580.00, '#b8e986', '两侧看台固定座椅，舒适观演体验'),
  ('area-lay-galaxy-stand-3', 'lay-galaxy-mixed', '看台三区', 'STAND_3', 'BALCONY', TRUE, 300, 480.00, '#bd10e0', '后方看台固定座椅，超值音乐之夜');

INSERT IGNORE INTO seat_layout_seat (id, layout_id, area_id, seat_code, row_no, col_no, section, status, price)
SELECT
  CONCAT('lay-galaxy-mixed:seat-', r.row_no, '-', c.col_no),
  'lay-galaxy-mixed',
  CASE
    WHEN r.row_no <= 10 THEN 'area-lay-galaxy-stand-1'
    WHEN r.row_no <= 20 THEN 'area-lay-galaxy-stand-2'
    ELSE 'area-lay-galaxy-stand-3'
  END,
  CONCAT('seat-', r.row_no, '-', c.col_no),
  r.row_no,
  c.col_no,
  CASE
    WHEN r.row_no <= 10 THEN 'STAND_1'
    WHEN r.row_no <= 20 THEN 'STAND_2'
    ELSE 'STAND_3'
  END,
  'AVAILABLE',
  CASE
    WHEN r.row_no <= 10 THEN 680.00
    WHEN r.row_no <= 20 THEN 580.00
    ELSE 480.00
  END
FROM (
  SELECT 1 AS row_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
  UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
  UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
  UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20
  UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24 UNION ALL SELECT 25
  UNION ALL SELECT 26 UNION ALL SELECT 27 UNION ALL SELECT 28 UNION ALL SELECT 29 UNION ALL SELECT 30
) r
CROSS JOIN (
  SELECT 1 AS col_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
  UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
  UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
  UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20
  UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24 UNION ALL SELECT 25
  UNION ALL SELECT 26 UNION ALL SELECT 27 UNION ALL SELECT 28 UNION ALL SELECT 29 UNION ALL SELECT 30
) c
WHERE
  (r.row_no <= 20 AND c.col_no <= 20)
  OR (r.row_no > 20 AND c.col_no <= 30);

UPDATE show_schedule
SET
  hall_id = 'hall-main',
  layout_id = 'lay-main-seated',
  business_date = DATE(start_time),
  sale_start_time = COALESCE(sale_start_time, '2026-01-01 00:00:00'),
  sale_end_time = COALESCE(sale_end_time, start_time),
  publish_status = COALESCE(publish_status, 'PUBLISHED')
WHERE id IN ('sch-101', 'sch-102');

UPDATE show_schedule
SET
  hall_id = 'hall-opera',
  layout_id = 'lay-opera-seated',
  business_date = DATE(start_time),
  sale_start_time = COALESCE(sale_start_time, '2026-01-01 00:00:00'),
  sale_end_time = COALESCE(sale_end_time, start_time),
  publish_status = COALESCE(publish_status, 'PUBLISHED')
WHERE id = 'sch-201';

UPDATE show_schedule
SET
  hall_id = 'hall-stadium',
  business_date = DATE(start_time),
  sale_start_time = COALESCE(sale_start_time, '2026-07-01 00:00:00'),
  sale_end_time = COALESCE(sale_end_time, start_time),
  publish_status = COALESCE(publish_status, 'PUBLISHED')
WHERE id = 'sch-301';

UPDATE show_schedule
SET
  hall_id = 'hall-imax',
  layout_id = 'lay-imax-seated',
  business_date = DATE(start_time),
  sale_start_time = COALESCE(sale_start_time, '2026-08-01 00:00:00'),
  sale_end_time = COALESCE(sale_end_time, start_time),
  publish_status = COALESCE(publish_status, 'PUBLISHED')
WHERE id = 'sch-401';

UPDATE show_schedule
SET
  hall_id = 'hall-galaxy',
  layout_id = 'lay-galaxy-mixed',
  business_date = DATE(start_time),
  sale_start_time = COALESCE(sale_start_time, '2026-06-01 00:00:00'),
  sale_end_time = COALESCE(sale_end_time, start_time),
  publish_status = COALESCE(publish_status, 'PUBLISHED')
WHERE id = 'sch-501';

INSERT IGNORE INTO user_account (id, username, password, role, display_name, status)
VALUES
  ('u-102', 'friend', '123', 'user', '拼座好友', 'ACTIVE');

INSERT IGNORE INTO encore_show (
  id, title, subtitle, cover_url, description, intro, cast_members, creative_team, full_synopsis, duration, category, tags, status, sort_order
)
VALUES (
  's-006',
  '茶馆',
  '老舍经典话剧',
  'https://images.unsplash.com/photo-1503095396549-807759245b35?q=80&w=1000&auto=format&fit=crop',
  '三幕人生，一座茶馆，照见时代变迁。',
  '老舍先生代表作，以裕泰茶馆为窗口，展现半个世纪的世道人心。',
  '王利发、常四爷、秦仲义、松二爷、庞太监及裕泰茶馆众人',
  '编剧：老舍；导演：ENCORE 话剧制作组；舞美：ENCORE Stage Lab',
  '故事围绕北京裕泰茶馆展开，三幕跨越清末、民初与抗战后时期。茶馆老板王利发努力周旋于各色人物之间，常四爷、秦仲义等人的命运在时代洪流中起落。作品以小小茶馆承载社会众生相，既有市井烟火，也有深沉悲悯。',
  120,
  'Play',
  JSON_ARRAY('话剧', '经典', '中文'),
  'PUBLISHED',
  25
);

UPDATE encore_show
SET
  intro = COALESCE(intro, '老舍先生代表作，以裕泰茶馆为窗口，展现半个世纪的世道人心。'),
  cast_members = COALESCE(cast_members, '王利发、常四爷、秦仲义、松二爷、庞太监及裕泰茶馆众人'),
  creative_team = COALESCE(creative_team, '编剧：老舍；导演：ENCORE 话剧制作组；舞美：ENCORE Stage Lab'),
  full_synopsis = COALESCE(full_synopsis, '故事围绕北京裕泰茶馆展开，三幕跨越清末、民初与抗战后时期。茶馆老板王利发努力周旋于各色人物之间，常四爷、秦仲义等人的命运在时代洪流中起落。作品以小小茶馆承载社会众生相，既有市井烟火，也有深沉悲悯。')
WHERE id = 's-006';

INSERT IGNORE INTO encore_show (id, title, subtitle, cover_url, description, duration, category, tags, status, sort_order)
VALUES (
  's-005',
  '星河回响演唱会',
  'Echo of Galaxy Concert',
  'https://images.unsplash.com/photo-1506157786151-b8491531f063?q=80&w=1000&auto=format&fit=crop',
  '星河回响，震撼来袭！全新混合票务模式体验演唱会。',
  150,
  'Concert',
  JSON_ARRAY('Live', 'Mixed Mode', 'Premium'),
  'PUBLISHED',
  5
);

UPDATE encore_show
SET
  intro = COALESCE(intro, '星河回响演唱会采用混合票务模式，内场站席与看台座席共同组成沉浸式音乐现场。'),
  cast_members = COALESCE(cast_members, '星河回响乐队、特邀嘉宾、现场弦乐组、电子视觉团队'),
  creative_team = COALESCE(creative_team, '音乐制作：ENCORE Live Studio；舞美设计：Galaxy Stage Lab；灯光视觉：Aurora Crew'),
  full_synopsis = COALESCE(full_synopsis, '整场演唱会分为“启航”“穿越”“回声”“归来”四个篇章，从电子流行、摇滚段落到大型合唱逐步推进。观众可选择内场站立区域近距离参与，也可选择看台固定座席俯瞰全场灯海。')
WHERE id = 's-005';

INSERT IGNORE INTO show_schedule (
  id, show_id, hall_id, layout_id, theater_name, business_date, start_time, end_time, sale_start_time, sale_end_time, status, publish_status, price_range, ticket_mode
)
VALUES
  ('sch-musical-default', 's-001', 'hall-main', 'lay-main-seated', 'Main Hall', '2026-06-20', '2026-06-20 19:30:00', '2026-06-20 22:00:00', '2026-06-01 00:00:00', '2026-06-20 19:30:00', 'ON_SALE', 'PUBLISHED', '$50 - $150', 'SEATED'),
  ('sch-play-default', 's-006', 'hall-main', 'lay-main-seated', 'Main Hall', '2026-06-21', '2026-06-21 19:30:00', '2026-06-21 21:30:00', '2026-06-01 00:00:00', '2026-06-21 19:30:00', 'ON_SALE', 'PUBLISHED', '￥80 - ￥180', 'SEATED'),
  ('sch-ballet-default', 's-002', 'hall-opera', 'lay-opera-seated', 'Opera House', '2026-06-22', '2026-06-22 19:30:00', '2026-06-22 21:30:00', '2026-06-01 00:00:00', '2026-06-22 19:30:00', 'ON_SALE', 'PUBLISHED', '$80 - $200', 'SEATED'),
  ('sch-movie-default', 's-004', 'hall-imax', 'lay-imax-seated', 'IMAX Cinema', '2026-06-23', '2026-06-23 14:00:00', '2026-06-23 17:00:00', '2026-06-01 00:00:00', '2026-06-23 14:00:00', 'ON_SALE', 'PUBLISHED', '$20 - $30', 'SEATED'),
  ('sch-501', 's-005', 'hall-galaxy', 'lay-galaxy-mixed', '星河体育场', '2026-07-20', '2026-07-20 19:30:00', '2026-07-20 22:00:00', '2026-06-01 00:00:00', '2026-07-20 19:30:00', 'ON_SALE', 'PUBLISHED', '￥480 - ￥1680', 'MIXED');

UPDATE show_schedule
SET
  hall_id = 'hall-galaxy',
  layout_id = 'lay-galaxy-mixed',
  business_date = DATE(start_time),
  sale_start_time = COALESCE(sale_start_time, '2026-06-01 00:00:00'),
  sale_end_time = COALESCE(sale_end_time, start_time),
  publish_status = COALESCE(publish_status, 'PUBLISHED'),
  status = CASE WHEN status IN ('COMING_SOON', 'PREPARING') THEN 'ON_SALE' ELSE status END
WHERE id = 'sch-501';

INSERT IGNORE INTO venue_area (id, hall_id, name, code, area_type, is_seated, capacity, base_price, available_count, locked_count, sold_count, color, description, position_data)
SELECT
  area.id,
  layout.hall_id,
  area.name,
  area.code,
  area.area_type,
  area.is_seated,
  area.capacity,
  area.base_price,
  area.capacity,
  0,
  0,
  area.color,
  area.description,
  area.position_data
FROM seat_layout_area area
JOIN seat_layout layout ON layout.id = area.layout_id;

INSERT IGNORE INTO schedule_area_inventory (id, schedule_id, area_id, price, total_count, available_count, locked_count, sold_count, status)
SELECT
  LEFT(CONCAT('inv-', REPLACE(schedule.id, 'sch-', ''), '-', LOWER(REPLACE(area.code, '_', '-'))), 32),
  schedule.id,
  area.id,
  area.base_price,
  area.capacity,
  area.capacity,
  0,
  0,
  'AVAILABLE'
FROM show_schedule schedule
JOIN seat_layout_area area ON area.layout_id = schedule.layout_id
WHERE schedule.ticket_mode IN ('ZONED', 'MIXED')
  AND NOT EXISTS (
    SELECT 1
    FROM schedule_area_inventory existing
    WHERE existing.schedule_id = schedule.id
  );

INSERT IGNORE INTO schedule_seat (id, schedule_id, seat_code, row_no, col_no, section, status, price, area_id)
SELECT
  CONCAT(schedule.id, ':', seat.seat_code),
  schedule.id,
  seat.seat_code,
  seat.row_no,
  seat.col_no,
  seat.section,
  seat.status,
  seat.price,
  seat.area_id
FROM show_schedule schedule
JOIN seat_layout_seat seat ON seat.layout_id = schedule.layout_id
WHERE schedule.id IN ('sch-musical-default', 'sch-play-default', 'sch-ballet-default', 'sch-movie-default', 'sch-501');
