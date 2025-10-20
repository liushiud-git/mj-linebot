# liushiud-mj-linebot

一個以 **Java + Spring Boot + LINE Bot SDK** 建立的麻將戰績統計機器人，
資料庫採用 **Turso (libSQL / 雲端 SQLite)**。

## 功能
- `/add` 新增一場戰績（可選日期），例如：  
  `/add A +2000 B -1500 C -500 D 0`  
  或  
  `/add 2025-10-19T20:01 A +2000 B -1500 C -500 D 0`
- `/status` 顯示每位玩家的 **總分** 與 **標準差**（依總分由高到低）
- `/show10` 顯示最近 **10 場回合** 的每人分數

> 每次 `/add` 會同時：  
> 1. 寫入 `mahjong_rounds` 與 `mahjong_records`  
> 2. 重新計算每位玩家的總分與標準差，更新至 `mahjong_summary`

---

## 資料表結構

- `mahjong_rounds(id, datetime)`：回合表
- `mahjong_records(id, round_id, datetime, player, score)`：每局明細
- `mahjong_summary(player, total_score, stddev)`：玩家總結

資料表會在應用程式啟動時自動建立（`src/main/resources/schema.sql`）。

---

## Turso 連線設定

請先建立 Turso 資料庫，並取得連線資訊（URL 與 Token）。
在部署環境（例如 Render）設定環境變數：

```
TURSO_DB_URL=libsql://你的db.turso.io
TURSO_DB_TOKEN=你的token
LINE_CHANNEL_TOKEN=你的LineBot Token
LINE_CHANNEL_SECRET=你的LineBot Secret
```

`application.yml` 中已設定使用 `org.sqlite.JDBC`，配合 DBeaver 的 LibSQL JDBC 驅動。
（依 2025/10 資訊，`com.dbeaver.jdbc:com.dbeaver.jdbc.driver.libsql:1.0.4` 可正常連線 Turso）

---

## 本機開發（含 ngrok 測試）

1. 設定環境變數（可用 `.env` 或你的 shell）：
   ```bash
   export TURSO_DB_URL=libsql://xxx.turso.io
   export TURSO_DB_TOKEN=eyJ...
   export LINE_CHANNEL_TOKEN=xxx
   export LINE_CHANNEL_SECRET=xxx
   ```
2. 啟動：
   ```bash
   ./mvnw spring-boot:run
   ```
3. 用 ngrok 開啟 8080：
   ```bash
   ngrok http 8080
   ```
4. 把 `https://xxxxx.ngrok.io/callback` 填到 LINE Developer → Webhook URL，點 **Verify**。

---

## 部署到 Render（免費方案）

- **Build Command**：`mvn clean package -DskipTests`
- **Start Command**：`java -jar target/liushiud-mj-linebot-1.0.0.jar`
- 設定環境變數：`TURSO_DB_URL`、`TURSO_DB_TOKEN`、`LINE_CHANNEL_TOKEN`、`LINE_CHANNEL_SECRET`
- Render 產生的網域 + `/callback` 設為 LINE Webhook URL

> 免費方案若服務閒置可能會休眠，但資料保存在 **Turso**（不會遺失）。

---

## 指令說明

- `/add A +2000 B -1500 C -500 D 0`
  - 預設使用「現在（台北時區）」時間
- `/add 2025-10-19T20:05 A +200 B -200`
  - 也可自帶時間（`YYYY-MM-DD` 或 `YYYY-MM-DDTHH:mm` 或完整 ISO8601）
- `/status`
  - 依總分排序，顯示：`玩家：總分 X，標準差 Y.Y`
- `/show10`
  - 依回合時間由近到遠，列出各回合玩家分數

---

## 注意事項

- 玩家名稱以空白分隔；分數請填整數（可帶正負號）。
- 標準差用公式：`sqrt( (sum(score^2)/n) - (avg^2) )`。
- 若未提供時間，系統會用台北時區的當下時間，並以 ISO8601 回存。

祝你麻將一路自摸、胡到天聽 🀄🎉
