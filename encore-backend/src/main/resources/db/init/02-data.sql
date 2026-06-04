SET NAMES utf8mb4;
USE encore;

INSERT IGNORE INTO user_account (id, username, password, role, display_name, status)
VALUES
  ('u-101', 'user', '123', 'user', '普通用户', 'ACTIVE'),
  ('u-102', 'friend', '123', 'user', '拼座好友', 'ACTIVE'),
  ('u-901', 'admin', '123', 'admin', '票务管理员', 'ACTIVE'),
  ('u-801', 'checker', '123', 'checker', '检票员', 'ACTIVE'),
  ('u-701', 'sysadmin', '123', 'sysadmin', '系统管理员', 'ACTIVE');

INSERT IGNORE INTO encore_show (
  id, title, subtitle, cover_url, description, duration, category, tags, status, sort_order
)
VALUES
  (
    's-001',
    'THE PHANTOM OF THE OPERA',
    'Classic Musical',
    'https://images.unsplash.com/photo-1507676184212-d0330a157088?q=80&w=1000&auto=format&fit=crop',
    'The brilliant original production of Andrew Lloyd Webber''s classic musical.',
    150,
    'Musical',
    JSON_ARRAY('Classic', 'Must See'),
    'PUBLISHED',
    10
  ),
  (
    's-002',
    'SWAN LAKE',
    'Tchaikovsky Ballet',
    'https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?q=80&w=1000&auto=format&fit=crop',
    'A masterpiece of classical ballet with a timeless score.',
    120,
    'Ballet',
    JSON_ARRAY('Dance', 'Romantic'),
    'PUBLISHED',
    20
  ),
  (
    's-003',
    'COLDPLAY: MUSIC OF THE SPHERES',
    'World Tour Concert',
    'https://images.unsplash.com/photo-1540039155732-684736dd61dc?q=80&w=1000&auto=format&fit=crop',
    'Experience the magic of Coldplay live in concert.',
    180,
    'Concert',
    JSON_ARRAY('Live', 'Pop'),
    'PUBLISHED',
    30
  ),
  (
    's-004',
    'DUNE: PART TWO',
    'Sci-Fi Epic',
    'https://images.unsplash.com/photo-1536440136628-849c177e76a1?q=80&w=1000&auto=format&fit=crop',
    'The saga continues as Paul Atreides unites with Chani and the Fremen.',
    166,
    'Movie',
    JSON_ARRAY('Sci-Fi', 'IMAX'),
    'PUBLISHED',
    40
  );

UPDATE encore_show
SET
  intro = '安德鲁·劳埃德·韦伯经典音乐剧，讲述巴黎歌剧院幽暗传说与天才魅影的爱恋执念。',
  cast_members = '克里斯汀、魅影、拉乌尔、卡洛塔及巴黎歌剧院合唱团',
  creative_team = '作曲：安德鲁·劳埃德·韦伯；原著：加斯东·勒鲁；舞台监督：ENCORE 剧院制作组',
  full_synopsis = '年轻女高音克里斯汀在巴黎歌剧院崭露头角，却被隐藏在剧院深处的神秘魅影牵引。魅影以音乐为纽带训练她、保护她，也试图占有她。当克里斯汀与旧友拉乌尔重逢，爱、才华、恐惧与自由之间的冲突逐渐推向剧院地下湖畔的终局。'
WHERE id = 's-001';

UPDATE encore_show
SET
  intro = '柴可夫斯基传世芭蕾，以纯白群舞、浪漫双人舞和命运悲剧呈现古典芭蕾之美。',
  cast_members = '奥杰塔/奥吉莉娅、齐格弗里德王子、罗特巴特、王后及天鹅群舞',
  creative_team = '作曲：彼得·伊里奇·柴可夫斯基；编舞：经典马林斯基版本改编；演出：ENCORE 芭蕾舞团',
  full_synopsis = '王子齐格弗里德在湖畔邂逅被魔法变成天鹅的公主奥杰塔，并许下真爱誓言。魔王罗特巴特以黑天鹅奥吉莉娅设下骗局，使王子在舞会上误认爱人。誓言破碎之后，奥杰塔与王子以爱对抗诅咒，在月色湖畔迎来悲怆而高贵的结局。'
WHERE id = 's-002';

UPDATE encore_show
SET
  intro = 'Coldplay 世界巡演特别场，以沉浸灯光、全场合唱和星河视觉打造大型音乐现场。',
  cast_members = 'Coldplay 乐队、巡演乐手、现场和声与舞美团队',
  creative_team = '音乐总监：Coldplay；舞美设计：Music of the Spheres 巡演团队；本地执行：ENCORE Live',
  full_synopsis = '演唱会围绕“星球、爱与连接”展开，串联多首代表作品与新专辑曲目。观众将在星河灯海、腕带互动和环绕音响中共同完成一场大型合唱，体验从温柔抒情到全场沸腾的完整音乐旅程。'
WHERE id = 's-003';

UPDATE encore_show
SET
  intro = '科幻史诗续章，保罗·厄崔迪在沙丘世界中面对复仇、信仰与命运的抉择。',
  cast_members = '保罗·厄崔迪、契妮、杰西卡夫人、弗雷曼人、哈克南家族',
  creative_team = '导演：丹尼斯·维伦纽瓦；原著：弗兰克·赫伯特；放映版本：IMAX 特别版',
  full_synopsis = '保罗与母亲进入弗雷曼人的世界，在沙漠中学习生存、战斗与信仰。他与契妮的关系逐渐加深，同时也被预言和权力推向更危险的位置。面对家族仇恨、帝国政治和宗教狂热，保罗必须选择自己将成为怎样的领袖。'
WHERE id = 's-004';

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
  CASE
    WHEN r.row_no <= 3 THEN 'VIP'
    WHEN r.row_no <= 7 THEN 'A'
    ELSE 'B'
  END,
  CASE
    WHEN c.col_no = 8 AND r.row_no IN (1, 5, 9) THEN 'DISABLED'
    ELSE 'AVAILABLE'
  END,
  CASE
    WHEN r.row_no <= 3 THEN layouts.vip_price
    WHEN r.row_no <= 7 THEN layouts.standard_price
    ELSE layouts.economy_price
  END
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

INSERT IGNORE INTO show_schedule (
  id, show_id, hall_id, layout_id, theater_name, business_date, start_time, end_time, sale_start_time, sale_end_time, status, publish_status, price_range
)
VALUES
  ('sch-101', 's-001', 'hall-main', 'lay-main-seated', 'Main Hall', '2026-05-24', '2026-05-24 19:30:00', '2026-05-24 22:00:00', '2026-01-01 00:00:00', '2026-05-24 19:30:00', 'ON_SALE', 'PUBLISHED', '$50 - $150'),
  ('sch-102', 's-001', 'hall-main', 'lay-main-seated', 'Main Hall', '2026-05-25', '2026-05-25 14:00:00', '2026-05-25 16:30:00', '2026-01-01 00:00:00', '2026-05-25 14:00:00', 'ON_SALE', 'PUBLISHED', '$50 - $150'),
  ('sch-201', 's-002', 'hall-opera', 'lay-opera-seated', 'Opera House', '2026-06-10', '2026-06-10 20:00:00', '2026-06-10 22:00:00', '2026-01-01 00:00:00', '2026-06-10 20:00:00', 'ON_SALE', 'PUBLISHED', '$80 - $200'),
  ('sch-301', 's-003', 'hall-stadium', NULL, 'Grand Stadium', '2026-08-15', '2026-08-15 20:00:00', '2026-08-15 23:00:00', '2026-07-01 00:00:00', '2026-08-15 20:00:00', 'PREPARING', 'PUBLISHED', '$120 - $500'),
  ('sch-401', 's-004', 'hall-imax', 'lay-imax-seated', 'IMAX Cinema', '2026-09-01', '2026-09-01 14:00:00', '2026-09-01 17:00:00', '2026-08-01 00:00:00', '2026-09-01 14:00:00', 'COMING_SOON', 'PUBLISHED', '$20 - $30');

UPDATE show_schedule
SET
  hall_id = CASE id
    WHEN 'sch-101' THEN 'hall-main'
    WHEN 'sch-102' THEN 'hall-main'
    WHEN 'sch-201' THEN 'hall-opera'
    WHEN 'sch-301' THEN 'hall-stadium'
    WHEN 'sch-401' THEN 'hall-imax'
    ELSE hall_id
  END,
  layout_id = CASE id
    WHEN 'sch-101' THEN 'lay-main-seated'
    WHEN 'sch-102' THEN 'lay-main-seated'
    WHEN 'sch-201' THEN 'lay-opera-seated'
    WHEN 'sch-401' THEN 'lay-imax-seated'
    ELSE layout_id
  END,
  business_date = DATE(start_time),
  publish_status = COALESCE(publish_status, 'PUBLISHED')
WHERE id IN ('sch-101', 'sch-102', 'sch-201', 'sch-301', 'sch-401');

INSERT IGNORE INTO schedule_seat (
  id, schedule_id, seat_code, row_no, col_no, section, status, price
)
SELECT
  CONCAT(s.id, ':seat-', r.row_no, '-', c.col_no) AS id,
  s.id AS schedule_id,
  CONCAT('seat-', r.row_no, '-', c.col_no) AS seat_code,
  r.row_no,
  c.col_no,
  CASE
    WHEN r.row_no <= 3 THEN 'VIP'
    WHEN r.row_no <= 7 THEN 'A'
    ELSE 'B'
  END AS section,
  CASE
    WHEN c.col_no = 8 AND r.row_no IN (1, 5, 9) THEN 'DISABLED'
    WHEN r.row_no = 10 AND c.col_no IN (3, 4, 5) THEN 'SOLD'
    ELSE 'AVAILABLE'
  END AS status,
  CASE
    WHEN r.row_no <= 3 THEN 150
    WHEN r.row_no <= 7 THEN 100
    ELSE 50
  END AS price
FROM show_schedule s
CROSS JOIN (
  SELECT 1 AS row_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
  UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
) r
CROSS JOIN (
  SELECT 1 AS col_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
  UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
  UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
) c
WHERE s.ticket_mode = 'SEATED';

-- Concert Mock Data s-005
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
  intro = '星河回响演唱会采用混合票务模式，内场站席与看台座席共同组成沉浸式音乐现场。',
  cast_members = '星河回响乐队、特邀嘉宾、现场弦乐组、电子视觉团队',
  creative_team = '音乐制作：ENCORE Live Studio；舞美设计：Galaxy Stage Lab；灯光视觉：Aurora Crew',
  full_synopsis = '整场演唱会分为“启航”“穿越”“回声”“归来”四个篇章，从电子流行、摇滚段落到大型合唱逐步推进。观众可选择内场站立区域近距离参与，也可选择看台固定座席俯瞰全场灯海。'
WHERE id = 's-005';

INSERT IGNORE INTO show_schedule (
  id, show_id, hall_id, layout_id, theater_name, business_date, start_time, end_time, sale_start_time, sale_end_time, status, publish_status, price_range, ticket_mode
)
VALUES (
  'sch-501',
  's-005',
  'hall-galaxy',
  'lay-galaxy-mixed',
  '星河体育场',
  '2026-07-20',
  '2026-07-20 19:30:00',
  '2026-07-20 22:00:00',
  '2026-06-01 00:00:00',
  '2026-07-20 19:30:00',
  'ON_SALE',
  'PUBLISHED',
  '￥480 - ￥1680',
  'MIXED'
);

UPDATE show_schedule
SET
  hall_id = 'hall-galaxy',
  layout_id = 'lay-galaxy-mixed',
  business_date = DATE(start_time),
  sale_start_time = COALESCE(sale_start_time, '2026-06-01 00:00:00'),
  sale_end_time = COALESCE(sale_end_time, start_time),
  publish_status = COALESCE(publish_status, 'PUBLISHED')
WHERE id = 'sch-501';

INSERT IGNORE INTO venue_area (id, hall_id, name, code, area_type, is_seated, capacity, base_price, available_count, locked_count, sold_count, color, description)
VALUES
  ('area-vip-a', '星河体育场', 'VIP A区', 'VIP_A', 'VIP', FALSE, 80, 1680.00, 80, 0, 0, '#c8955a', '舞台正前超近距离站席，尽享震撼音效'),
  ('area-infield-a', '星河体育场', '内场A区', 'INFIELD_A', 'FIELD', FALSE, 300, 1280.00, 300, 0, 0, '#4a90e2', '内场前排站立区，现场气氛极佳'),
  ('area-infield-b', '星河体育场', '内场B区', 'INFIELD_B', 'FIELD', FALSE, 500, 980.00, 500, 0, 0, '#50e3c2', '内场后排站立区，性价比极高'),
  ('area-stand-1', '星河体育场', '看台一区', 'STAND_1', 'BALCONY', TRUE, 200, 680.00, 200, 0, 0, '#f5a623', '一侧看台固定座椅，绝佳全景视野'),
  ('area-stand-2', '星河体育场', '看台二区', 'STAND_2', 'BALCONY', TRUE, 200, 580.00, 200, 0, 0, '#b8e986', '两侧看台固定座椅，舒适观演体验'),
  ('area-stand-3', '星河体育场', '看台三区', 'STAND_3', 'BALCONY', TRUE, 300, 480.00, 300, 0, 0, '#bd10e0', '后方看台固定座椅，超值音乐之夜');

INSERT IGNORE INTO schedule_area_inventory (id, schedule_id, area_id, price, total_count, available_count, locked_count, sold_count, status)
VALUES
  ('inv-501-vip-a', 'sch-501', 'area-vip-a', 1680.00, 80, 80, 0, 0, 'AVAILABLE'),
  ('inv-501-infield-a', 'sch-501', 'area-infield-a', 1280.00, 300, 300, 0, 0, 'AVAILABLE'),
  ('inv-501-infield-b', 'sch-501', 'area-infield-b', 980.00, 500, 500, 0, 0, 'AVAILABLE'),
  ('inv-501-stand-1', 'sch-501', 'area-stand-1', 680.00, 200, 200, 0, 0, 'AVAILABLE'),
  ('inv-501-stand-2', 'sch-501', 'area-stand-2', 580.00, 200, 200, 0, 0, 'AVAILABLE'),
  ('inv-501-stand-3', 'sch-501', 'area-stand-3', 480.00, 300, 300, 0, 0, 'AVAILABLE');

INSERT IGNORE INTO schedule_seat (id, schedule_id, seat_code, row_no, col_no, section, status, price, area_id)
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
) c;

INSERT IGNORE INTO schedule_seat (id, schedule_id, seat_code, row_no, col_no, section, status, price, area_id)
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
) c;

INSERT IGNORE INTO schedule_seat (id, schedule_id, seat_code, row_no, col_no, section, status, price, area_id)
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
) c;

-- Default category coverage and future ON_SALE schedules (kept in sync with Flyway V2).
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
