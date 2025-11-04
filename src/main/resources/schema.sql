DROP TABLE IF EXISTS mahjong_summary;
DROP TABLE IF EXISTS mahjong_records;
DROP TABLE IF EXISTS mahjong_rounds;

CREATE TABLE mahjong_rounds (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  round_date TEXT UNIQUE NOT NULL
);

CREATE TABLE mahjong_records (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  round_id INTEGER NOT NULL,
  round_date TEXT NOT NULL,
  player TEXT NOT NULL,
  score INTEGER NOT NULL,
  FOREIGN KEY (round_id) REFERENCES mahjong_rounds(id)
);

CREATE INDEX idx_records_round ON mahjong_records(round_id);
CREATE INDEX idx_records_player ON mahjong_records(player);

CREATE TABLE mahjong_summary (
  player TEXT PRIMARY KEY,
  total_score INTEGER NOT NULL,
  win_count INTEGER NOT NULL,
  lose_count INTEGER NOT NULL
);
