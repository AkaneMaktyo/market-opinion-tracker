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
  notify_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  history_seed_mode VARCHAR(32) NOT NULL,
  seed_completed_at VARCHAR(64),
  created_at VARCHAR(64) NOT NULL,
  updated_at VARCHAR(64) NOT NULL,
  UNIQUE KEY uq_wxpusher_blogger_name(blogger_name),
  INDEX idx_wxpusher_blogger_enabled(enabled, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE wxpusher_bloggers ADD COLUMN notify_enabled BOOLEAN NOT NULL DEFAULT TRUE;

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

CREATE TABLE IF NOT EXISTS youtube_channels (
  id VARCHAR(64) PRIMARY KEY,
  channel_id VARCHAR(64) NOT NULL,
  title VARCHAR(255) NOT NULL,
  handle VARCHAR(255) NOT NULL,
  source_url VARCHAR(500) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  last_checked_at VARCHAR(64),
  last_video_published_at VARCHAR(64),
  created_at VARCHAR(64) NOT NULL,
  updated_at VARCHAR(64) NOT NULL,
  UNIQUE KEY uq_youtube_channel_remote(channel_id),
  INDEX idx_youtube_channels_updated(updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE live_sessions ADD INDEX idx_sessions_kol_date(kol_id, session_date);

CREATE TABLE IF NOT EXISTS schema_migrations (
  id VARCHAR(120) PRIMARY KEY,
  applied_at VARCHAR(64) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS youtube_videos (
  video_id VARCHAR(64) PRIMARY KEY,
  channel_row_id VARCHAR(64) NOT NULL,
  channel_id VARCHAR(64) NOT NULL,
  title VARCHAR(500) NOT NULL,
  video_url VARCHAR(1000) NOT NULL,
  published_at VARCHAR(64) NOT NULL,
  audio_path VARCHAR(1000),
  audio_duration_ms BIGINT NOT NULL DEFAULT 0,
  transcript_status VARCHAR(32) NOT NULL,
  transcript_language VARCHAR(32) NOT NULL DEFAULT '',
  transcript_source VARCHAR(64) NOT NULL DEFAULT '',
  transcript_text MEDIUMTEXT,
  transcript_segments_json MEDIUMTEXT,
  error_message TEXT,
  notify_status VARCHAR(32) NOT NULL DEFAULT '',
  notify_error TEXT,
  notified_at VARCHAR(64),
  read_at VARCHAR(64),
  synced_at VARCHAR(64),
  created_at VARCHAR(64) NOT NULL,
  updated_at VARCHAR(64) NOT NULL,
  INDEX idx_youtube_videos_channel(channel_row_id),
  INDEX idx_youtube_videos_published(published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE youtube_videos ADD COLUMN notify_status VARCHAR(32) NOT NULL DEFAULT '';
ALTER TABLE youtube_videos ADD COLUMN notify_error TEXT;
ALTER TABLE youtube_videos ADD COLUMN notified_at VARCHAR(64);
ALTER TABLE youtube_videos ADD COLUMN read_at VARCHAR(64);

UPDATE youtube_videos
SET read_at = COALESCE(NULLIF(read_at, ''), updated_at)
WHERE NOT EXISTS (
  SELECT 1 FROM schema_migrations WHERE id = 'youtube_videos_read_at_existing'
)
  AND (read_at IS NULL OR read_at = '');

INSERT IGNORE INTO schema_migrations(id, applied_at)
VALUES ('youtube_videos_read_at_existing', NOW());

CREATE TABLE IF NOT EXISTS youtube_opinion_imports (
  video_id VARCHAR(64) PRIMARY KEY,
  status VARCHAR(32) NOT NULL,
  session_id VARCHAR(64),
  llm_output_json MEDIUMTEXT,
  error_message TEXT,
  imported_at VARCHAR(64),
  created_at VARCHAR(64) NOT NULL,
  updated_at VARCHAR(64) NOT NULL,
  INDEX idx_youtube_opinion_import_status(status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS llm_call_logs (
  id VARCHAR(64) PRIMARY KEY,
  scene VARCHAR(64) NOT NULL,
  model VARCHAR(120) NOT NULL,
  status VARCHAR(32) NOT NULL,
  request_chars INT NOT NULL,
  response_chars INT NOT NULL,
  duration_ms BIGINT NOT NULL,
  request_preview TEXT,
  response_preview TEXT,
  error_message TEXT,
  created_at VARCHAR(64) NOT NULL,
  INDEX idx_llm_call_scene(scene, created_at),
  INDEX idx_llm_call_status(status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS kol_positions (
  id VARCHAR(64) PRIMARY KEY,
  kol_id VARCHAR(64) NOT NULL,
  instrument_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  opened_at VARCHAR(64),
  closed_at VARCHAR(64),
  last_opinion_id VARCHAR(64),
  last_action VARCHAR(32) NOT NULL,
  created_at VARCHAR(64) NOT NULL,
  updated_at VARCHAR(64) NOT NULL,
  UNIQUE KEY uq_kol_position(kol_id, instrument_id),
  INDEX idx_kol_position_status(kol_id, status, updated_at),
  INDEX idx_kol_position_instrument(instrument_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS kol_instrument_groups (
  kol_id VARCHAR(64) NOT NULL,
  instrument_id VARCHAR(64) NOT NULL,
  group_name VARCHAR(255) NOT NULL,
  PRIMARY KEY (kol_id, instrument_id),
  INDEX idx_kol_instrument_group(kol_id, group_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO kol_instrument_groups(kol_id, instrument_id, group_name)
SELECT 'default', id, group_name
FROM instruments
WHERE group_name IS NOT NULL AND group_name <> '';

CREATE TABLE IF NOT EXISTS resonance_clusters (
  id VARCHAR(64) PRIMARY KEY,
  instrument_id VARCHAR(64) NOT NULL,
  symbol VARCHAR(64) NOT NULL,
  bucket_date VARCHAR(32) NOT NULL,
  direction VARCHAR(32) NOT NULL,
  horizon VARCHAR(64) NOT NULL,
  score INT NOT NULL,
  grade VARCHAR(32) NOT NULL,
  action VARCHAR(64) NOT NULL,
  summary TEXT,
  trigger_text TEXT,
  invalidation_text TEXT,
  risk_text TEXT,
  catalyst_text TEXT,
  source_count INT NOT NULL,
  opinion_count INT NOT NULL,
  support_count INT NOT NULL,
  conflict_count INT NOT NULL,
  source_names TEXT,
  last_opinion_at VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  alert_status VARCHAR(32) NOT NULL,
  alert_error TEXT,
  last_alert_at VARCHAR(64),
  created_at VARCHAR(64) NOT NULL,
  updated_at VARCHAR(64) NOT NULL,
  UNIQUE KEY uq_resonance_key(instrument_id, bucket_date, direction, horizon),
  INDEX idx_resonance_symbol(symbol, score, last_opinion_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resonance_cluster_items (
  id VARCHAR(64) PRIMARY KEY,
  cluster_id VARCHAR(64) NOT NULL,
  opinion_id VARCHAR(64) NOT NULL,
  role VARCHAR(32) NOT NULL,
  source_name VARCHAR(255) NOT NULL,
  direction VARCHAR(32) NOT NULL,
  horizon VARCHAR(64) NOT NULL,
  thesis TEXT,
  source_quote TEXT,
  opinion_time VARCHAR(64) NOT NULL,
  created_at VARCHAR(64) NOT NULL,
  UNIQUE KEY uq_resonance_item(cluster_id, opinion_id),
  INDEX idx_resonance_item_cluster(cluster_id, role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS resonance_alerts (
  id VARCHAR(64) PRIMARY KEY,
  cluster_id VARCHAR(64) NOT NULL,
  title VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  status VARCHAR(32) NOT NULL,
  error_message TEXT,
  sent_at VARCHAR(64),
  created_at VARCHAR(64) NOT NULL,
  INDEX idx_resonance_alert_cluster(cluster_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS signal_trade_plans (
  id VARCHAR(64) PRIMARY KEY,
  alert_id VARCHAR(64) NOT NULL,
  instrument_id VARCHAR(64) NOT NULL,
  asset_class VARCHAR(16) NOT NULL,
  provider VARCHAR(32) NOT NULL,
  exchange_symbol VARCHAR(64) NOT NULL,
  base_asset VARCHAR(32) NOT NULL,
  quote_asset VARCHAR(32) NOT NULL,
  side VARCHAR(8) NOT NULL,
  total_cost DECIMAL(24, 8) NOT NULL,
  batch_count INT NOT NULL,
  environment VARCHAR(32) NOT NULL,
  paper BOOLEAN NOT NULL DEFAULT TRUE,
  status VARCHAR(32) NOT NULL,
  error_message TEXT,
  created_at VARCHAR(64) NOT NULL,
  updated_at VARCHAR(64) NOT NULL,
  UNIQUE KEY uq_signal_trade_alert(alert_id),
  INDEX idx_signal_trade_status(provider, status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS signal_trade_orders (
  id VARCHAR(64) PRIMARY KEY,
  plan_id VARCHAR(64) NOT NULL,
  batch_no INT NOT NULL,
  exchange_symbol VARCHAR(64) NOT NULL,
  side VARCHAR(8) NOT NULL,
  order_type VARCHAR(16) NOT NULL,
  price DECIMAL(24, 8) NOT NULL,
  planned_cost DECIMAL(24, 8) NOT NULL,
  quantity DECIMAL(32, 12) NOT NULL,
  client_order_id VARCHAR(64) NOT NULL,
  exchange_order_id VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  executed_quantity DECIMAL(32, 12) NOT NULL DEFAULT 0,
  cumulative_quote DECIMAL(24, 8) NOT NULL DEFAULT 0,
  average_price DECIMAL(24, 8) NOT NULL DEFAULT 0,
  error_message TEXT,
  created_at VARCHAR(64) NOT NULL,
  updated_at VARCHAR(64) NOT NULL,
  UNIQUE KEY uq_signal_trade_batch(plan_id, batch_no),
  UNIQUE KEY uq_signal_trade_client_order(client_order_id),
  INDEX idx_signal_trade_order_status(status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS position_cost_overrides (
  provider VARCHAR(32) NOT NULL,
  symbol VARCHAR(64) NOT NULL,
  average_cost DECIMAL(24, 8) NOT NULL,
  updated_at VARCHAR(64) NOT NULL,
  PRIMARY KEY (provider, symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS position_cost_anchors (
  provider VARCHAR(32) NOT NULL,
  symbol VARCHAR(64) NOT NULL,
  basis_quantity DECIMAL(32, 12) NOT NULL,
  basis_cost DECIMAL(24, 8) NOT NULL,
  trade_quantity DECIMAL(32, 12) NOT NULL DEFAULT 0,
  trade_quote DECIMAL(24, 8) NOT NULL DEFAULT 0,
  updated_at VARCHAR(64) NOT NULL,
  PRIMARY KEY (provider, symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS kol_instrument_watchlist (
  kol_id VARCHAR(64) NOT NULL,
  instrument_id VARCHAR(64) NOT NULL,
  watch_state VARCHAR(16) NOT NULL,
  created_at VARCHAR(64) NOT NULL,
  updated_at VARCHAR(64) NOT NULL,
  PRIMARY KEY (kol_id, instrument_id),
  INDEX idx_instrument_watchlist(instrument_id, watch_state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE llm_call_logs ADD COLUMN message_id VARCHAR(64);
ALTER TABLE llm_call_logs ADD COLUMN request_body LONGTEXT;
ALTER TABLE llm_call_logs ADD COLUMN response_body LONGTEXT;
ALTER TABLE llm_call_logs ADD COLUMN http_status INT;
ALTER TABLE llm_call_logs ADD COLUMN provider_request_id VARCHAR(255);
ALTER TABLE llm_call_logs ADD COLUMN prompt_tokens INT NOT NULL DEFAULT 0;
ALTER TABLE llm_call_logs ADD COLUMN completion_tokens INT NOT NULL DEFAULT 0;
ALTER TABLE llm_call_logs ADD COLUMN total_tokens INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS message_price_alert_recognitions (
  id VARCHAR(64) PRIMARY KEY,
  message_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  ocr_text LONGTEXT,
  candidates_json LONGTEXT,
  warnings_json TEXT,
  error_message TEXT,
  created_at VARCHAR(64) NOT NULL,
  updated_at VARCHAR(64) NOT NULL,
  UNIQUE KEY uq_price_alert_recognition_message(message_id),
  INDEX idx_price_alert_recognition_status(status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS price_signal_alerts (
  id VARCHAR(64) PRIMARY KEY,
  instrument_id VARCHAR(64) NOT NULL,
  alert_type VARCHAR(16) NOT NULL DEFAULT 'RANGE',
  trigger_direction VARCHAR(16) NOT NULL DEFAULT 'ANY',
  lower_price DECIMAL(24, 8) NOT NULL,
  upper_price DECIMAL(24, 8) NOT NULL,
  target_price DECIMAL(24, 8),
  status VARCHAR(32) NOT NULL,
  last_price DECIMAL(24, 8),
  last_checked_at VARCHAR(64),
  triggered_at VARCHAR(64),
  notify_status VARCHAR(32) NOT NULL,
  error_message TEXT,
  source_recognition_id VARCHAR(64),
  source_candidate_id VARCHAR(64),
  created_at VARCHAR(64) NOT NULL,
  updated_at VARCHAR(64) NOT NULL,
  INDEX idx_price_signal_active(status, instrument_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE price_signal_alerts ADD COLUMN trigger_direction VARCHAR(16) NOT NULL DEFAULT 'ANY';
ALTER TABLE price_signal_alerts ADD COLUMN source_recognition_id VARCHAR(64);
ALTER TABLE price_signal_alerts ADD COLUMN source_candidate_id VARCHAR(64);
CREATE UNIQUE INDEX uq_price_signal_source
  ON price_signal_alerts(source_recognition_id, source_candidate_id);
