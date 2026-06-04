SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS encore
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE encore;

CREATE TABLE IF NOT EXISTS user_account (
  id VARCHAR(32) PRIMARY KEY,
  username VARCHAR(64) NOT NULL UNIQUE,
  password VARCHAR(128) NOT NULL,
  role VARCHAR(32) NOT NULL,
  display_name VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user_account_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS encore_show (
  id VARCHAR(32) PRIMARY KEY,
  title VARCHAR(128) NOT NULL,
  subtitle VARCHAR(128) NOT NULL,
  cover_url VARCHAR(512) NOT NULL,
  description TEXT NOT NULL,
  intro TEXT NULL,
  cast_members TEXT NULL,
  creative_team TEXT NULL,
  full_synopsis TEXT NULL,
  duration INT NOT NULL,
  category VARCHAR(64) NOT NULL,
  tags JSON NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_encore_show_category (category),
  INDEX idx_encore_show_status_sort (status, sort_order)
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

CREATE TABLE IF NOT EXISTS show_schedule (
  id VARCHAR(32) PRIMARY KEY,
  show_id VARCHAR(32) NOT NULL,
  hall_id VARCHAR(32) NULL,
  layout_id VARCHAR(32) NULL,
  theater_name VARCHAR(128) NOT NULL,
  business_date DATE NULL,
  start_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  sale_start_time DATETIME NULL,
  sale_end_time DATETIME NULL,
  status VARCHAR(32) NOT NULL,
  publish_status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
  price_range VARCHAR(64) NOT NULL,
  ticket_mode VARCHAR(32) NOT NULL DEFAULT 'SEATED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_show_schedule_show_time (show_id, start_time),
  INDEX idx_show_schedule_hall_time (hall_id, start_time, end_time),
  INDEX idx_show_schedule_business_date (business_date),
  CONSTRAINT fk_show_schedule_show
    FOREIGN KEY (show_id) REFERENCES encore_show (id)
    ON DELETE CASCADE,
  CONSTRAINT fk_show_schedule_hall
    FOREIGN KEY (hall_id) REFERENCES venue_hall (id),
  CONSTRAINT fk_show_schedule_layout
    FOREIGN KEY (layout_id) REFERENCES seat_layout (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
