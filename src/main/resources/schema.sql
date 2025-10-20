-- 建立回合表：每次 /add 產生一個回合
CREATE TABLE IF NOT EXISTS mahjong_rounds (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  datetime TEXT NOT NULL
);

-- 每局明細：一個回合對應多位玩家分數
CREATE TABLE IF NOT EXISTS mahjong_records (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  round_id INTEGER NOT NULL,
  datetime TEXT NOT NULL,
  player TEXT NOT NULL,
  score INTEGER NOT NULL,
  FOREIGN KEY (round_id) REFERENCES mahjong_rounds(id)
);

CREATE INDEX IF NOT EXISTS idx_records_round ON mahjong_records(round_id);
CREATE INDEX IF NOT EXISTS idx_records_player ON mahjong_records(player);

-- 玩家總結表
CREATE TABLE IF NOT EXISTS mahjong_summary (
  player TEXT PRIMARY KEY,
  total_score INTEGER NOT NULL,
  stddev REAL NOT NULL
);
