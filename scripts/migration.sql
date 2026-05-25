SET NAMES utf8mb4;
USE encore;

-- 1. Alter existing tables
ALTER TABLE show_schedule ADD COLUMN ticket_mode VARCHAR(32) NOT NULL DEFAULT 'SEATED';
ALTER TABLE schedule_seat ADD COLUMN area_id VARCHAR(32) NULL;
ALTER TABLE ticket_item MODIFY COLUMN seat_id VARCHAR(32) NULL;
ALTER TABLE ticket_item ADD COLUMN area_inventory_id VARCHAR(32) NULL;

-- 2. Create venue_area table
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

-- 3. Create schedule_area_inventory table
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

-- 4. Insert mock show "星河回响演唱会"
INSERT INTO encore_show (id, title, subtitle, cover_url, description, duration, category, tags, status, sort_order)
VALUES (
  's-005',
  '星河回响演唱会',
  'Echo of Galaxy Concert',
  'https://images.unsplash.com/photo-1506157786151-b8491531f063?q=80&w=1000&auto=format&fit=crop',
  '星河回响，震撼来袭！全新混合票务模式体验演唱会。',
  150,
  'Concert',
  '["Live", "Mixed Mode", "Premium"]',
  'PUBLISHED',
  5
) ON DUPLICATE KEY UPDATE title=VALUES(title);

-- 5. Insert mock schedule for s-005
INSERT INTO show_schedule (id, show_id, theater_name, start_time, end_time, status, price_range, ticket_mode)
VALUES (
  'sch-501',
  's-005',
  '星河体育场',
  '2026-07-20 19:30:00',
  '2026-07-20 22:00:00',
  'ON_SALE',
  '￥480 - ￥1680',
  'MIXED'
) ON DUPLICATE KEY UPDATE theater_name=VALUES(theater_name);

-- 6. Insert venue areas for 星河体育场
INSERT INTO venue_area (id, hall_id, name, code, area_type, is_seated, capacity, base_price, available_count, locked_count, sold_count, color, description)
VALUES
  ('area-vip-a', '星河体育场', 'VIP A区', 'VIP_A', 'VIP', FALSE, 80, 1680.00, 80, 0, 0, '#c8955a', '舞台正前超近距离站席，尽享震撼音效'),
  ('area-infield-a', '星河体育场', '内场A区', 'INFIELD_A', 'FIELD', FALSE, 300, 1280.00, 300, 0, 0, '#4a90e2', '内场前排站立区，现场气氛极佳'),
  ('area-infield-b', '星河体育场', '内场B区', 'INFIELD_B', 'FIELD', FALSE, 500, 980.00, 500, 0, 0, '#50e3c2', '内场后排站立区，性价比极高'),
  ('area-stand-1', '星河体育场', '看台一区', 'STAND_1', 'BALCONY', TRUE, 200, 680.00, 200, 0, 0, '#f5a623', '一侧看台固定座椅，绝佳全景视野'),
  ('area-stand-2', '星河体育场', '看台二区', 'STAND_2', 'BALCONY', TRUE, 200, 580.00, 200, 0, 0, '#b8e986', '两侧看台固定座椅，舒适观演体验'),
  ('area-stand-3', '星河体育场', '看台三区', 'STAND_3', 'BALCONY', TRUE, 300, 480.00, 300, 0, 0, '#bd10e0', '后方看台固定座椅，超值音乐之夜')
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 7. Insert schedule area inventories for sch-501
INSERT INTO schedule_area_inventory (id, schedule_id, area_id, price, total_count, available_count, locked_count, sold_count, status)
VALUES
  ('inv-501-vip-a', 'sch-501', 'area-vip-a', 1680.00, 80, 80, 0, 0, 'AVAILABLE'),
  ('inv-501-infield-a', 'sch-501', 'area-infield-a', 1280.00, 300, 300, 0, 0, 'AVAILABLE'),
  ('inv-501-infield-b', 'sch-501', 'area-infield-b', 980.00, 500, 500, 0, 0, 'AVAILABLE'),
  ('inv-501-stand-1', 'sch-501', 'area-stand-1', 680.00, 200, 200, 0, 0, 'AVAILABLE'),
  ('inv-501-stand-2', 'sch-501', 'area-stand-2', 580.00, 200, 200, 0, 0, 'AVAILABLE'),
  ('inv-501-stand-3', 'sch-501', 'area-stand-3', 480.00, 300, 300, 0, 0, 'AVAILABLE')
ON DUPLICATE KEY UPDATE price=VALUES(price);

-- 8. Generate seats for Stands 1, 2, 3 for sch-501
-- Stands 1: 10 rows x 20 cols = 200 seats. Rows 1-10, cols 1-20
-- Stands 2: 10 rows x 20 cols = 200 seats. Rows 11-20, cols 1-20
-- Stands 3: 10 rows x 30 cols = 300 seats. Rows 21-30, cols 1-30

-- For Stands 1
INSERT INTO schedule_seat (id, schedule_id, seat_code, row_no, col_no, section, status, price, area_id)
SELECT
  CONCAT('sch-501:seat-', r.row_no, '-', c.col_no) AS id,
  'sch-501' AS schedule_id,
  CONCAT('seat-', r.row_no, '-', c.col_no) AS seat_code,
  r.row_no,
  c.col_no,
  'STAND_1' AS section,
  'AVAILABLE' AS status,
  680.00 AS price,
  'area-stand-1' AS area_id
FROM (
  SELECT 1 AS row_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
  UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
) r
CROSS JOIN (
  SELECT 1 AS col_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
  UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
  UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
  UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20
) c
ON DUPLICATE KEY UPDATE price=VALUES(price);

-- For Stands 2
INSERT INTO schedule_seat (id, schedule_id, seat_code, row_no, col_no, section, status, price, area_id)
SELECT
  CONCAT('sch-501:seat-', r.row_no, '-', c.col_no) AS id,
  'sch-501' AS schedule_id,
  CONCAT('seat-', r.row_no, '-', c.col_no) AS seat_code,
  r.row_no,
  c.col_no,
  'STAND_2' AS section,
  'AVAILABLE' AS status,
  580.00 AS price,
  'area-stand-2' AS area_id
FROM (
  SELECT 11 AS row_no UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
  UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20
) r
CROSS JOIN (
  SELECT 1 AS col_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
  UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
  UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
  UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20
) c
ON DUPLICATE KEY UPDATE price=VALUES(price);

-- For Stands 3
INSERT INTO schedule_seat (id, schedule_id, seat_code, row_no, col_no, section, status, price, area_id)
SELECT
  CONCAT('sch-501:seat-', r.row_no, '-', c.col_no) AS id,
  'sch-501' AS schedule_id,
  CONCAT('seat-', r.row_no, '-', c.col_no) AS seat_code,
  r.row_no,
  c.col_no,
  'STAND_3' AS section,
  'AVAILABLE' AS status,
  480.00 AS price,
  'area-stand-3' AS area_id
FROM (
  SELECT 21 AS row_no UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24 UNION ALL SELECT 25
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
ON DUPLICATE KEY UPDATE price=VALUES(price);
