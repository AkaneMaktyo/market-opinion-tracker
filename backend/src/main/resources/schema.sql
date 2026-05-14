CREATE TABLE IF NOT EXISTS kols (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL UNIQUE,
  description TEXT,
  created_at TEXT NOT NULL
);

INSERT OR IGNORE INTO kols(id, name, description, created_at)
VALUES ('default', '默认KOL', '系统自动创建的默认来源', datetime('now'));

UPDATE kols
SET name = '默认KOL', description = '系统自动创建的默认来源'
WHERE id = 'default';

CREATE TABLE IF NOT EXISTS instruments (
  id TEXT PRIMARY KEY,
  symbol TEXT NOT NULL UNIQUE,
  name TEXT,
  market TEXT,
  sector TEXT,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS live_sessions (
  id TEXT PRIMARY KEY,
  kol_id TEXT,
  session_date TEXT NOT NULL,
  title TEXT NOT NULL,
  source TEXT,
  raw_text TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS opinions (
  id TEXT PRIMARY KEY,
  session_id TEXT NOT NULL,
  instrument_id TEXT NOT NULL,
  direction TEXT NOT NULL,
  horizon TEXT NOT NULL,
  thesis TEXT NOT NULL,
  trigger_condition TEXT,
  invalidation TEXT,
  confidence INTEGER,
  source_quote TEXT,
  reference_price REAL,
  raw_direction TEXT,
  risks_text TEXT,
  catalysts_text TEXT,
  price_notes_text TEXT,
  raw_item_json TEXT,
  opinion_time TEXT NOT NULL,
  status TEXT NOT NULL,
  created_at TEXT NOT NULL,
  FOREIGN KEY(session_id) REFERENCES live_sessions(id),
  FOREIGN KEY(instrument_id) REFERENCES instruments(id)
);

CREATE TABLE IF NOT EXISTS price_levels (
  id TEXT PRIMARY KEY,
  opinion_id TEXT NOT NULL,
  level_type TEXT NOT NULL,
  price REAL NOT NULL,
  note TEXT,
  FOREIGN KEY(opinion_id) REFERENCES opinions(id)
);

CREATE TABLE IF NOT EXISTS reviews (
  id TEXT PRIMARY KEY,
  opinion_id TEXT NOT NULL UNIQUE,
  outcome TEXT NOT NULL,
  notes TEXT,
  result_price REAL,
  review_date TEXT NOT NULL,
  created_at TEXT NOT NULL,
  FOREIGN KEY(opinion_id) REFERENCES opinions(id)
);

CREATE TABLE IF NOT EXISTS market_bars (
  id TEXT PRIMARY KEY,
  instrument_id TEXT NOT NULL,
  timeframe TEXT NOT NULL,
  bar_time TEXT NOT NULL,
  open REAL NOT NULL,
  high REAL NOT NULL,
  low REAL NOT NULL,
  close REAL NOT NULL,
  volume REAL NOT NULL,
  FOREIGN KEY(instrument_id) REFERENCES instruments(id),
  UNIQUE(instrument_id, timeframe, bar_time)
);

CREATE INDEX IF NOT EXISTS idx_opinions_instrument ON opinions(instrument_id);
CREATE INDEX IF NOT EXISTS idx_bars_lookup ON market_bars(instrument_id, timeframe, bar_time);

ALTER TABLE live_sessions ADD COLUMN kol_id TEXT;
ALTER TABLE opinions ADD COLUMN raw_direction TEXT;
ALTER TABLE opinions ADD COLUMN risks_text TEXT;
ALTER TABLE opinions ADD COLUMN catalysts_text TEXT;
ALTER TABLE opinions ADD COLUMN price_notes_text TEXT;
ALTER TABLE opinions ADD COLUMN raw_item_json TEXT;

UPDATE live_sessions SET kol_id = 'default'
WHERE kol_id IS NULL OR kol_id = '';

UPDATE live_sessions
SET title = '示例直播',
    raw_text = 'NVDA：回踩关键支撑后仍偏强，突破前高可以继续看多。'
WHERE source = 'Codex Demo';

UPDATE opinions
SET horizon = '短线',
    thesis = '回踩不破支撑，资金仍在高景气方向。',
    trigger_condition = '放量突破前高',
    invalidation = '跌破支撑位后观点失效',
    source_quote = '回踩关键支撑后仍偏强，突破前高可以继续看多。',
    raw_direction = '看多',
    risks_text = '跌破支撑位后观点失效',
    catalysts_text = 'AI 资金主线',
    price_notes_text = '830 支撑
910 目标'
WHERE session_id IN (
  SELECT id FROM live_sessions WHERE source = 'Codex Demo'
);

CREATE INDEX IF NOT EXISTS idx_sessions_kol ON live_sessions(kol_id);
