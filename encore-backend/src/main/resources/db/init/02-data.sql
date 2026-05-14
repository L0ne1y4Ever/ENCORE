USE encore;

INSERT INTO user_account (id, username, password, role, display_name, status)
VALUES
  ('u-101', 'user', '123', 'user', '普通用户', 'ACTIVE'),
  ('u-901', 'admin', '123', 'admin', '票务管理员', 'ACTIVE'),
  ('u-801', 'checker', '123', 'checker', '检票员', 'ACTIVE'),
  ('u-701', 'sysadmin', '123', 'sysadmin', '系统管理员', 'ACTIVE')
ON DUPLICATE KEY UPDATE
  password = VALUES(password),
  role = VALUES(role),
  display_name = VALUES(display_name),
  status = VALUES(status);

INSERT INTO encore_show (
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
  )
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  subtitle = VALUES(subtitle),
  cover_url = VALUES(cover_url),
  description = VALUES(description),
  duration = VALUES(duration),
  category = VALUES(category),
  tags = VALUES(tags),
  status = VALUES(status),
  sort_order = VALUES(sort_order);

INSERT INTO show_schedule (
  id, show_id, theater_name, start_time, end_time, status, price_range
)
VALUES
  ('sch-101', 's-001', 'Main Hall', '2026-05-24 19:30:00', '2026-05-24 22:00:00', 'ON_SALE', '$50 - $150'),
  ('sch-102', 's-001', 'Main Hall', '2026-05-25 14:00:00', '2026-05-25 16:30:00', 'ON_SALE', '$50 - $150'),
  ('sch-201', 's-002', 'Opera House', '2026-06-10 20:00:00', '2026-06-10 22:00:00', 'ON_SALE', '$80 - $200'),
  ('sch-301', 's-003', 'Grand Stadium', '2026-08-15 20:00:00', '2026-08-15 23:00:00', 'PREPARING', '$120 - $500'),
  ('sch-401', 's-004', 'IMAX Cinema', '2026-09-01 14:00:00', '2026-09-01 17:00:00', 'COMING_SOON', '$20 - $30')
ON DUPLICATE KEY UPDATE
  theater_name = VALUES(theater_name),
  start_time = VALUES(start_time),
  end_time = VALUES(end_time),
  status = VALUES(status),
  price_range = VALUES(price_range);

INSERT INTO schedule_seat (
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
WHERE TRUE
ON DUPLICATE KEY UPDATE
  row_no = VALUES(row_no),
  col_no = VALUES(col_no),
  section = VALUES(section),
  price = VALUES(price);
