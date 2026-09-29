# liushiud-mj-linebot

一個以 **Java 21 + Spring Boot 3 + LINE Bot SDK** 建立的麻將戰績統計機器人，
資料庫採用 **Turso (libSQL / 雲端 SQLite)**。

## 指令

| 指令 | 別名 | 說明 |
|---|---|---|
| `/add 20251017 戰績：隨 -7700,蕭 -2100,馬 5700,堂 3700,鳥 400` | | 登錄某一天的戰績 |
| `/status` | `排行榜` | **今年**（台北時區）排行榜 |
| `/statusall` | `全部排行榜` | 歷年全部排行榜 |
| `/show` | `全部戰績` | 依日期列出所有戰績 |
| `/del 20251017` | | 刪除某一天的戰績 |

### `/add` 格式說明
- 日期固定 `yyyyMMdd`，一天只保留一筆；同一天重複 `/add` 會**覆蓋**舊資料。
- `戰績` 後可接半形 `:` 或全形 `：`。
- 玩家之間以 `,`、`，` 或 `、` 分隔；名字與分數之間以空白分隔，分數為整數（可帶正負號）。
- 任何一段格式錯誤或玩家重複時，整筆都不會寫入。
- 部分暱稱會自動轉換（見 `ScoreService.rename()`），例如 `隨` → `隨緣`、`馬`／`快` → `快馬`。

### 排行榜計算
- 總分：該期間所有場次分數加總，依總分由高到低排序。
- 勝敗：分數 > 0 記一勝、< 0 記一敗，0 分不計。勝率 = 勝 ÷ (勝 + 敗)。
- 另列出該期間的單場最高分與最低分（同分則全部列出）。

---

## 架構

```
controller/MahjongBotController   LINE webhook（/callback），依文字分派指令
controller/HealthController       GET /healthz → OK
service/ScoreService              指令解析、SQL、訊息排版
resources/schema.sql              建表腳本（需手動執行）
```

## 資料表結構

- `mahjong_rounds(id, round_date)`：每天一筆，`round_date` 為 `yyyyMMdd`，UNIQUE
- `mahjong_records(id, round_id, round_date, player, score)`：每位玩家每場一筆

應用程式**不會**自動建表（`spring.sql.init.mode: never`）。
首次建立資料庫時，請手動執行 `src/main/resources/schema.sql`（例如 `turso db shell <db> < schema.sql`）。
此腳本使用 `IF NOT EXISTS`，重複執行不會影響既有資料。

> 注意：DBeaver libSQL JDBC driver 透過 HTTP 逐句執行，**不支援 transaction**。

---

## 連線設定

在部署環境設定下列環境變數，並在 `application.yml` 以 `${...}` 引用：

```
TURSO_DB_URL=libsql://你的db.turso.io
TURSO_DB_TOKEN=你的token
LINE_CHANNEL_TOKEN=你的LineBot Token
LINE_CHANNEL_SECRET=你的LineBot Secret
```

JDBC driver：`com.dbeaver.jdbc:com.dbeaver.jdbc.driver.libsql:1.0.4`（`com.dbeaver.jdbc.driver.libsql.LibSqlDriver`）。

---

## 本機開發（含 ngrok 測試）

1. 設定上述環境變數。
2. 啟動：
   ```bash
   mvn spring-boot:run
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
- 可用 `/healthz` 作為健康檢查路徑

> 免費方案若服務閒置可能會休眠，但資料保存在 **Turso**（不會遺失）。

也可使用 `Dockerfile`（需先 `mvn package` 產生 JAR）。
