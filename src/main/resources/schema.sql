-- 可重複執行：只建立不存在的資料表，不會刪除既有資料

CREATE TABLE IF NOT EXISTS mahjong_rounds (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  round_date TEXT UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS mahjong_records (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  round_id INTEGER NOT NULL,
  round_date TEXT NOT NULL,
  player TEXT NOT NULL,
  score INTEGER NOT NULL,
  FOREIGN KEY (round_id) REFERENCES mahjong_rounds(id)
);

CREATE INDEX IF NOT EXISTS idx_records_round ON mahjong_records(round_id);
CREATE INDEX IF NOT EXISTS idx_records_player ON mahjong_records(player);
CREATE INDEX IF NOT EXISTS idx_records_date ON mahjong_records(round_date);
