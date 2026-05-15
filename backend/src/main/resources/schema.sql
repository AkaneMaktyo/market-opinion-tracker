CREATE TABLE IF NOT EXISTS kols (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(120) NOT NULL UNIQUE,
  description TEXT,
  created_at VARCHAR(64) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO kols(id, name, description, created_at)
VALUES ('default', '默认KOL', '系统自动创建的默认来源', NOW());

UPDATE kols
SET name = '默认KOL', description = '系统自动创建的默认来源'
WHERE id = 'default';

CREATE TABLE IF NOT EXISTS instruments (
  id VARCHAR(64) PRIMARY KEY,
  symbol VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(255),
  market VARCHAR(32),
  sector VARCHAR(255),
  created_at VARCHAR(64) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS live_sessions (
  id VARCHAR(64) PRIMARY KEY,
  kol_id VARCHAR(64) NOT NULL DEFAULT 'default',
  session_date VARCHAR(32) NOT NULL,
  title VARCHAR(255) NOT NULL,
  source VARCHAR(120),
  raw_text MEDIUMTEXT NOT NULL,
  created_at VARCHAR(64) NOT NULL,
  INDEX idx_sessions_kol(kol_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS opinions (
  id VARCHAR(64) PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  instrument_id VARCHAR(64) NOT NULL,
  direction VARCHAR(32) NOT NULL,
  horizon VARCHAR(64) NOT NULL,
  thesis TEXT NOT NULL,
  trigger_condition TEXT,
  invalidation TEXT,
  confidence INT,
  source_quote TEXT,
  reference_price DECIMAL(20, 4),
  raw_direction VARCHAR(255),
  risks_text TEXT,
  catalysts_text TEXT,
  price_notes_text TEXT,
  raw_item_json MEDIUMTEXT,
  opinion_time VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at VARCHAR(64) NOT NULL,
  INDEX idx_opinions_instrument(instrument_id),
  CONSTRAINT fk_opinions_session FOREIGN KEY(session_id) REFERENCES live_sessions(id),
  CONSTRAINT fk_opinions_instrument FOREIGN KEY(instrument_id) REFERENCES instruments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS price_levels (
  id VARCHAR(64) PRIMARY KEY,
  opinion_id VARCHAR(64) NOT NULL,
  level_type VARCHAR(32) NOT NULL,
  price DECIMAL(20, 4) NOT NULL,
  note TEXT,
  CONSTRAINT fk_levels_opinion FOREIGN KEY(opinion_id) REFERENCES opinions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reviews (
  id VARCHAR(64) PRIMARY KEY,
  opinion_id VARCHAR(64) NOT NULL UNIQUE,
  outcome VARCHAR(32) NOT NULL,
  notes TEXT,
  result_price DECIMAL(20, 4),
  review_date VARCHAR(32) NOT NULL,
  created_at VARCHAR(64) NOT NULL,
  CONSTRAINT fk_reviews_opinion FOREIGN KEY(opinion_id) REFERENCES opinions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS market_bars (
  id VARCHAR(64) PRIMARY KEY,
  instrument_id VARCHAR(64) NOT NULL,
  timeframe VARCHAR(16) NOT NULL,
  bar_time VARCHAR(32) NOT NULL,
  open DECIMAL(20, 4) NOT NULL,
  high DECIMAL(20, 4) NOT NULL,
  low DECIMAL(20, 4) NOT NULL,
  close DECIMAL(20, 4) NOT NULL,
  volume DECIMAL(24, 4) NOT NULL,
  UNIQUE KEY uq_bars_lookup(instrument_id, timeframe, bar_time),
  INDEX idx_bars_lookup(instrument_id, timeframe, bar_time),
  CONSTRAINT fk_bars_instrument FOREIGN KEY(instrument_id) REFERENCES instruments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
