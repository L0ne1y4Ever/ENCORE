-- V6: 将 SEATED 座位定价调整为贴近真实剧场的分布 —— 最前排最便宜(B)、
-- 中段视野最佳最贵(VIP)、后段中等(A)。与 AdminService.generateSeatPool /
-- VenueManagementService.generateSeatedLayout 的新行分配规则保持一致：
--   front_rows  = rows >= 3 ? max(1, round(rows * 0.2)) : 0
--   premium_end = max(front_rows + 1, ceil(rows * 0.65))
-- 三档价格从既有座位价推导：最高价 = VIP，最低价 = B，次高价 = A（两档/单价布局自然降级）。
-- 仅重排纯座位池（area_id IS NULL）；MIXED 看台座位按区域单价计价，不参与。
-- 已售(SOLD)座位保留原 section/price，保证历史订单与票面金额一致。

-- 1) SEATED 布局模板（seat_layout_seat），影响后续新建场次的快照
UPDATE seat_layout_seat sls
JOIN (
  SELECT
    base.layout_id,
    CASE WHEN base.max_row >= 3 THEN GREATEST(1, ROUND(base.max_row * 0.2)) ELSE 0 END AS front_rows,
    GREATEST(GREATEST(1, ROUND(base.max_row * 0.2)) + 1, CEILING(base.max_row * 0.65)) AS premium_end,
    base.p_premium,
    base.p_standard,
    base.p_economy
  FROM (
    SELECT
      s.layout_id,
      MAX(s.row_no) AS max_row,
      MAX(s.price) AS p_premium,
      MIN(s.price) AS p_economy,
      COALESCE(MAX(CASE WHEN s.price < g.top_price THEN s.price END), MIN(s.price)) AS p_standard
    FROM seat_layout_seat s
    JOIN (
      SELECT layout_id, MAX(price) AS top_price
      FROM seat_layout_seat
      WHERE area_id IS NULL
      GROUP BY layout_id
    ) g ON g.layout_id = s.layout_id
    WHERE s.area_id IS NULL
    GROUP BY s.layout_id
  ) base
) agg ON agg.layout_id = sls.layout_id
SET
  sls.section = CASE
    WHEN sls.row_no <= agg.front_rows THEN 'B'
    WHEN sls.row_no <= agg.premium_end THEN 'VIP'
    ELSE 'A'
  END,
  sls.price = CASE
    WHEN sls.row_no <= agg.front_rows THEN agg.p_economy
    WHEN sls.row_no <= agg.premium_end THEN agg.p_premium
    ELSE agg.p_standard
  END
WHERE sls.area_id IS NULL;

-- 2) 既有场次座位池（schedule_seat），跳过已取消/已结束场次与已售座位
UPDATE schedule_seat ss
JOIN show_schedule sch ON sch.id = ss.schedule_id
JOIN (
  SELECT
    base.schedule_id,
    CASE WHEN base.max_row >= 3 THEN GREATEST(1, ROUND(base.max_row * 0.2)) ELSE 0 END AS front_rows,
    GREATEST(GREATEST(1, ROUND(base.max_row * 0.2)) + 1, CEILING(base.max_row * 0.65)) AS premium_end,
    base.p_premium,
    base.p_standard,
    base.p_economy
  FROM (
    SELECT
      s.schedule_id,
      MAX(s.row_no) AS max_row,
      MAX(s.price) AS p_premium,
      MIN(s.price) AS p_economy,
      COALESCE(MAX(CASE WHEN s.price < g.top_price THEN s.price END), MIN(s.price)) AS p_standard
    FROM schedule_seat s
    JOIN (
      SELECT schedule_id, MAX(price) AS top_price
      FROM schedule_seat
      WHERE area_id IS NULL
      GROUP BY schedule_id
    ) g ON g.schedule_id = s.schedule_id
    WHERE s.area_id IS NULL
    GROUP BY s.schedule_id
  ) base
) agg ON agg.schedule_id = ss.schedule_id
SET
  ss.section = CASE
    WHEN ss.row_no <= agg.front_rows THEN 'B'
    WHEN ss.row_no <= agg.premium_end THEN 'VIP'
    ELSE 'A'
  END,
  ss.price = CASE
    WHEN ss.row_no <= agg.front_rows THEN agg.p_economy
    WHEN ss.row_no <= agg.premium_end THEN agg.p_premium
    ELSE agg.p_standard
  END
WHERE ss.area_id IS NULL
  AND ss.status <> 'SOLD'
  AND sch.status NOT IN ('CANCELLED', 'ENDED');
