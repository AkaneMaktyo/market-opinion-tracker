CREATE TABLE IF NOT EXISTS kols (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(120) NOT NULL UNIQUE,
  description TEXT,
  created_at VARCHAR(64) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO kols(id, name, description, created_at)
VALUES ('default', '默认KOL', '系统自动创建的默认来源', NOW());

INSERT IGNORE INTO kols(id, name, description, created_at)
VALUES ('kzg', 'kzg', '测试阶段 JSON 直播观点来源', NOW());

UPDATE kols
SET name = '默认KOL', description = '系统自动创建的默认来源'
WHERE id = 'default';

CREATE TABLE IF NOT EXISTS instruments (
  id VARCHAR(64) PRIMARY KEY,
  symbol VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(255),
  market VARCHAR(32),
  sector VARCHAR(255),
  group_name VARCHAR(255),
  logo_url VARCHAR(500),
  market_data_provider VARCHAR(32),
  bitget_category VARCHAR(32),
  bitget_symbol VARCHAR(64),
  bitget_status VARCHAR(16),
  bitget_checked_at VARCHAR(64),
  created_at VARCHAR(64) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE instruments ADD COLUMN bitget_category VARCHAR(32);
ALTER TABLE instruments ADD COLUMN bitget_symbol VARCHAR(64);
ALTER TABLE instruments ADD COLUMN bitget_status VARCHAR(16);
ALTER TABLE instruments ADD COLUMN bitget_checked_at VARCHAR(64);
ALTER TABLE instruments ADD COLUMN group_name VARCHAR(255);
ALTER TABLE instruments ADD COLUMN logo_url VARCHAR(500);
ALTER TABLE instruments ADD COLUMN market_data_provider VARCHAR(32);

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
  INDEX idx_opinions_session(session_id),
  INDEX idx_opinions_instrument(instrument_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE opinions DROP FOREIGN KEY fk_opinions_session;
ALTER TABLE opinions DROP FOREIGN KEY fk_opinions_instrument;
ALTER TABLE opinions ADD COLUMN source_quote TEXT;
ALTER TABLE opinions ADD COLUMN raw_item_json MEDIUMTEXT;
ALTER TABLE opinions ADD INDEX idx_opinions_session(session_id);
ALTER TABLE opinions ADD INDEX idx_opinions_instrument(instrument_id);
ALTER TABLE opinions DROP INDEX fk_opinions_session;
ALTER TABLE opinions DROP INDEX fk_opinions_instrument;

UPDATE opinions
SET horizon = JSON_UNQUOTE(JSON_EXTRACT(raw_item_json, '$."周期"'))
WHERE JSON_VALID(raw_item_json)
  AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(raw_item_json, '$."周期"')), '') <> ''
  AND (horizon IS NULL OR horizon = '' OR horizon = '未指定');

CREATE TABLE IF NOT EXISTS price_levels (
  id VARCHAR(64) PRIMARY KEY,
  opinion_id VARCHAR(64) NOT NULL,
  level_type VARCHAR(32) NOT NULL,
  price DECIMAL(20, 4) NOT NULL,
  note TEXT,
  INDEX idx_levels_opinion(opinion_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE price_levels DROP FOREIGN KEY fk_levels_opinion;
ALTER TABLE price_levels ADD INDEX idx_levels_opinion(opinion_id);
ALTER TABLE price_levels DROP INDEX fk_levels_opinion;

CREATE TABLE IF NOT EXISTS reviews (
  id VARCHAR(64) PRIMARY KEY,
  opinion_id VARCHAR(64) NOT NULL UNIQUE,
  outcome VARCHAR(32) NOT NULL,
  notes TEXT,
  result_price DECIMAL(20, 4),
  review_date VARCHAR(32) NOT NULL,
  created_at VARCHAR(64) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE reviews DROP FOREIGN KEY fk_reviews_opinion;

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
  INDEX idx_bars_lookup(instrument_id, timeframe, bar_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE market_bars DROP FOREIGN KEY fk_bars_instrument;

CREATE TABLE IF NOT EXISTS exchange_credentials (
  id VARCHAR(64) PRIMARY KEY,
  provider VARCHAR(32) NOT NULL,
  account_type VARCHAR(32) NOT NULL,
  environment VARCHAR(32) NOT NULL,
  api_key VARCHAR(255) NOT NULL,
  api_secret VARCHAR(512) NOT NULL,
  passphrase VARCHAR(255) NOT NULL,
  product_type VARCHAR(32) NOT NULL,
  margin_coin VARCHAR(32) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at VARCHAR(64) NOT NULL,
  updated_at VARCHAR(64) NOT NULL,
  UNIQUE KEY uq_exchange_credential(provider, account_type, environment),
  INDEX idx_exchange_credential_enabled(provider, environment, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wxpusher_settings (
  id VARCHAR(32) PRIMARY KEY,
  device_token VARCHAR(255) NOT NULL,
  push_token VARCHAR(255) NOT NULL,
  device_uuid VARCHAR(255) NOT NULL,
  platform VARCHAR(64) NOT NULL,
  version VARCHAR(32) NOT NULL,
  poll_interval_seconds INT NOT NULL,
  enable_polling BOOLEAN NOT NULL DEFAULT FALSE,
  enable_websocket BOOLEAN NOT NULL DEFAULT FALSE,
  last_poll_at VARCHAR(64),
  last_heartbeat_at VARCHAR(64),
  last_error TEXT,
  created_at VARCHAR(64) NOT NULL,
  updated_at VARCHAR(64) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wxpusher_bloggers (
  id VARCHAR(64) PRIMARY KEY,
  kol_id VARCHAR(64) NOT NULL,
  blogger_name VARCHAR(255) NOT NULL,
  aliases_json TEXT NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  history_seed_mode VARCHAR(32) NOT NULL,
  seed_completed_at VARCHAR(64),
  created_at VARCHAR(64) NOT NULL,
  updated_at VARCHAR(64) NOT NULL,
  UNIQUE KEY uq_wxpusher_blogger_name(blogger_name),
  INDEX idx_wxpusher_blogger_enabled(enabled, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wxpusher_messages (
  id VARCHAR(64) PRIMARY KEY,
  message_key VARCHAR(512) NOT NULL,
  kol_id VARCHAR(64) NOT NULL,
  blogger_name VARCHAR(255) NOT NULL,
  title VARCHAR(500) NOT NULL,
  summary TEXT,
  detail_url VARCHAR(1000),
  source_url VARCHAR(1000),
  message_time VARCHAR(64) NOT NULL,
  raw_payload_json MEDIUMTEXT,
  detail_text MEDIUMTEXT,
  llm_output_json MEDIUMTEXT,
  status VARCHAR(32) NOT NULL,
  error_message TEXT,
  session_id VARCHAR(64),
  created_at VARCHAR(64) NOT NULL,
  updated_at VARCHAR(64) NOT NULL,
  UNIQUE KEY uq_wxpusher_message_key(message_key),
  INDEX idx_wxpusher_message_status(status, updated_at),
  INDEX idx_wxpusher_message_kol(kol_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wxpusher_raw_messages (
  id VARCHAR(64) PRIMARY KEY,
  message_key VARCHAR(512) NOT NULL,
  channel VARCHAR(32) NOT NULL,
  source_name VARCHAR(255) NOT NULL,
  title VARCHAR(500) NOT NULL,
  summary TEXT,
  detail_url VARCHAR(1000),
  source_url VARCHAR(1000),
  message_time VARCHAR(64) NOT NULL,
  raw_payload_json MEDIUMTEXT,
  created_at VARCHAR(64) NOT NULL,
  updated_at VARCHAR(64) NOT NULL,
  UNIQUE KEY uq_wxpusher_raw_message_key(message_key),
  INDEX idx_wxpusher_raw_message_time(message_time, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wxpusher_consumer_state (
  id VARCHAR(64) PRIMARY KEY,
  consumer_name VARCHAR(64) NOT NULL,
  message_key VARCHAR(512) NOT NULL,
  status VARCHAR(32) NOT NULL,
  error_message TEXT,
  derived_id VARCHAR(64),
  created_at VARCHAR(64) NOT NULL,
  updated_at VARCHAR(64) NOT NULL,
  UNIQUE KEY uq_wxpusher_consumer_message(consumer_name, message_key),
  INDEX idx_wxpusher_consumer_status(consumer_name, status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
