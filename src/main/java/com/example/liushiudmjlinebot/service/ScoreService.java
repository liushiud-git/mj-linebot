package com.example.liushiudmjlinebot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.*;

@Service
public class ScoreService {

	private final JdbcTemplate jdbc;
	private static final Logger log = LoggerFactory.getLogger(ScoreService.class);

	private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
	private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
	// 分隔線不可太長，否則手機上會自動換行
	private static final String DIVIDER = "──────────";
	private static final int RECENT_GAMES = 5;

	public ScoreService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	// DOTALL：手機上若把戰績分成多行輸入也能解析
	private static final Pattern LINE_PATTERN = Pattern.compile("^(?<date>\\d{8})\\s*戰績[:：]\\s*(?<pairs>.+)$", Pattern.DOTALL);
	// 玩家之間可用半形逗號、全形逗號或頓號分隔
	private static final Pattern PAIR_SEPARATOR = Pattern.compile("\\s*[,，、]\\s*");
	private static final Pattern NAME_SCORE = Pattern.compile("^(?<name>\\S+)\\s+(?<score>[+-]?\\d+)$");

	/**
	 * 增加一筆
	 * @param text
	 * @return
	 */
	public String addByFormattedLine(String text) {
		// 記錄目前執行到哪一步，出錯時可得知資料庫寫到一半的狀況（libSQL driver 不支援 transaction）
		String step = "解析";
		try {
			Matcher m = LINE_PATTERN.matcher(normalize(text));
			if (!m.matches())
				return "❌ 格式錯誤，請用：20251017 戰績：隨 -7700,蕭 -2100,馬 5700,堂 3700,鳥 400";
			String date = m.group("date");
			String pairs = m.group("pairs");

			log.info("date = " + date);
			log.info("pairs = " + pairs);

			if (!isValidDate(date))
				return "❌ 日期不正確：" + date;

			// 先解析完所有分數，確認無誤才動資料庫（libSQL driver 不支援 transaction）
			Map<String, Integer> scores = new LinkedHashMap<>();
			for (String seg : PAIR_SEPARATOR.split(pairs.trim())) {
				Matcher kv = NAME_SCORE.matcher(seg.trim());
				if (!kv.matches())
					return "❌ 無法解析「" + seg.trim() + "」，請用：名字 分數";
				String p = rename(kv.group("name"));
				int s = Integer.parseInt(kv.group("score"));
				if (scores.containsKey(p))
					return "❌ 玩家重複：" + p;
				scores.put(p, s);
			}
			if (scores.isEmpty())
				return "❌ 未寫入任何分數";

			step = "刪除當日舊資料";
			deleteByDate(date);

			step = "新增場次";
			jdbc.update("INSERT INTO mahjong_rounds(round_date) VALUES (?)", date);
			// 每個 statement 都是獨立的 HTTP 請求，last_insert_rowid() 不可靠，改用日期查回 id
			step = "查詢場次 id";
			Long roundId = jdbc.queryForObject("SELECT id FROM mahjong_rounds WHERE round_date=?", Long.class, date);

			StringBuilder msg = new StringBuilder();
			for (Map.Entry<String, Integer> e : scores.entrySet()) {
				String p = e.getKey();
				int s = e.getValue();
				step = "新增 " + p + " 的分數";
				jdbc.update("INSERT INTO mahjong_records(round_id,round_date,player,score) VALUES (?,?,?,?)",
						roundId, date, p, s);
				msg.append(String.format("%s %+d (%s)\n", p, s, s > 0 ? "1勝0敗" : s < 0 ? "0勝1敗" : "0勝0敗"));
			}
			return "✅ 已登錄 " + formatDate(date) + " 戰績\n" + msg.toString().trim();

		} catch (Exception ex) {
			log.error("新增戰績失敗（" + step + "）: " + text, ex);
			return "哎啊~新增有問題\n步驟：" + step + "\n原因：" + errorMessage(ex)
					+ (step.equals("解析") ? "" : "\n資料可能只寫入一半，請重新 /add 一次（會先清掉當日資料）");
		}

	}

	/**
	 * 統一輸入字元：全形空白、不換行空白、全形數字與正負號（手機輸入法常見）轉成半形
	 */
	private static String normalize(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFKC)
				.replace('−', '-') // 數學減號 −
				.trim();
	}

	/**
	 * 取出最底層例外的訊息，避免只顯示 Spring 包裝過的冗長 SQL 訊息
	 */
	private static String errorMessage(Throwable ex) {
		Throwable cause = NestedExceptionUtils.getMostSpecificCause(ex);
		String msg = cause.getMessage();
		if (msg == null || msg.isBlank())
			msg = cause.getClass().getSimpleName();
		// LINE 訊息不宜過長
		return msg.length() > 300 ? msg.substring(0, 300) + "…" : msg;
	}

	/**
	 * 刪除
	 * @param text
	 * @return
	 */
	public String deleteByDateCommand(String text) {
		String date = text.replaceAll("[^\\d]", "");
		if (date.length() != 8 || !isValidDate(date))
			return "❌ 請提供 yyyyMMdd 日期，例如：/del 20251017";
		try {
			int r = deleteByDate(date);
			return r == 0 ? "ℹ️ 該日期無資料" : "🗑 已刪除 " + formatDate(date) + " 戰績";
		} catch (Exception ex) {
			log.error("刪除戰績失敗: " + date, ex);
			return "哎啊~刪除有問題\n原因：" + errorMessage(ex);
		}
	}

	/**
	 * 依日期刪除
	 * @param date
	 * @return
	 */
	private int deleteByDate(String date) {
		// 以 round_date 刪除明細，避免舊資料 round_id 不正確而刪不乾淨
		int cnt = jdbc.update("DELETE FROM mahjong_records WHERE round_date=?", date);
		cnt += jdbc.update("DELETE FROM mahjong_rounds WHERE round_date=?", date);
		return cnt;
	}

	/**
	 * 今年的狀態
	 * @return
	 */
	public String status() {
		String yearPrefix = String.valueOf(Year.now(TAIPEI).getValue());
		return buildStatus("📊 " + yearPrefix + " 總戰績\n", yearPrefix + "%");
	}

	/**
	 * 所有的狀態
	 * @return
	 */
	public String statusAll() {
		return buildStatus("📊 歷年總戰績\n", "%");
	}

	/**
	 * 產生排行榜
	 * @param title 標題
	 * @param datePattern round_date 的 LIKE 條件（例如 "2025%"，全部則為 "%"）
	 */
	private String buildStatus(String title, String datePattern) {

		List<Map<String, Object>> rows = jdbc.queryForList(
				"SELECT player, " +
				"SUM(score) total, " +
				"SUM(CASE WHEN score > 0 THEN 1 ELSE 0 END) wins, " +
				"SUM(CASE WHEN score < 0 THEN 1 ELSE 0 END) loses " +
				"FROM mahjong_records " +
				"WHERE round_date LIKE ? " +
				"GROUP BY player", datePattern);

		if (rows.isEmpty()) {
			return "目前沒有任何戰績。";
		}
		rows.sort((a, b) -> Integer.compare(((Number) b.get("total")).intValue(), ((Number) a.get("total")).intValue()));

		StringBuilder sb = new StringBuilder(title);
		sb.append(DIVIDER).append("\n");

		// LINE 使用比例字型，無法用空白對齊欄位，改為每人兩行、每行控制在手機寬度內
		int rank = 0;
		for (Map<String, Object> r : rows) {

			String name = (String) r.get("player");
			int total = ((Number) r.get("total")).intValue();
			int wins = ((Number) r.get("wins")).intValue();
			int loses = ((Number) r.get("loses")).intValue();

			sb.append(String.format("%s %s %+,d\n", rankMark(++rank), name, total));
			sb.append(String.format("　　%d勝%d敗｜勝率 %.1f%%\n", wins, loses, winRate(wins, loses)));
		}

		// 1️⃣ 先查出最高與最低分值
		Integer maxScore = jdbc.queryForObject(
				"SELECT MAX(score) FROM mahjong_records WHERE round_date LIKE ?", Integer.class, datePattern);
		Integer minScore = jdbc.queryForObject(
				"SELECT MIN(score) FROM mahjong_records WHERE round_date LIKE ?", Integer.class, datePattern);

		// 2️⃣ 再取出同一期間內所有達到這個分數的紀錄
		List<Map<String, Object>> topWins = jdbc.queryForList(
				"SELECT round_date, player, score FROM mahjong_records WHERE score = ? AND round_date LIKE ? ORDER BY round_date",
				maxScore, datePattern);
		List<Map<String, Object>> topLoses = jdbc.queryForList(
				"SELECT round_date, player, score FROM mahjong_records WHERE score = ? AND round_date LIKE ? ORDER BY round_date",
				minScore, datePattern);

		// 3️⃣ 組合成字串輸出
		sb.append("\n🏆 單場勝最多\n");
		for (Map<String, Object> r : topWins) {
			sb.append(String.format("%s %+,d (%s)\n",
					r.get("player"), ((Number) r.get("score")).intValue(), formatDate2(r.get("round_date"))));
		}
		sb.append("💀 單場輸最多\n");
		for (Map<String, Object> r : topLoses) {
			sb.append(String.format("%s %+,d (%s)\n",
					r.get("player"), ((Number) r.get("score")).intValue(), formatDate2(r.get("round_date"))));
		}

		appendRecentGames(sb, datePattern);

		return sb.toString().trim();
	}

	/**
	 * 每位玩家在期間內最近 N 場（該玩家自己有打的場次）的戰績，依近期總分排序
	 */
	private void appendRecentGames(StringBuilder sb, String datePattern) {
		List<Map<String, Object>> rows = jdbc.queryForList(
				"SELECT player, score FROM mahjong_records WHERE round_date LIKE ? ORDER BY round_date DESC",
				datePattern);

		Map<String, List<Integer>> recent = new LinkedHashMap<>();
		for (Map<String, Object> r : rows) {
			List<Integer> list = recent.computeIfAbsent((String) r.get("player"), k -> new ArrayList<>());
			if (list.size() < RECENT_GAMES)
				list.add(((Number) r.get("score")).intValue());
		}

		List<Map.Entry<String, List<Integer>>> entries = new ArrayList<>(recent.entrySet());
		entries.sort((a, b) -> Integer.compare(sum(b.getValue()), sum(a.getValue())));

		sb.append("\n🔥 近").append(RECENT_GAMES).append("場戰績\n");
		sb.append(DIVIDER).append("\n");
		for (Map.Entry<String, List<Integer>> e : entries) {
			List<Integer> scores = e.getValue();
			int wins = (int) scores.stream().filter(s -> s > 0).count();
			int loses = (int) scores.stream().filter(s -> s < 0).count();
			// 場數不足 N 場時標註實際場數
			String games = scores.size() < RECENT_GAMES ? "(" + scores.size() + "場)" : "";
			sb.append(String.format("%s %d勝%d敗 %+,d 勝率 %.0f%%%s\n",
					e.getKey(), wins, loses, sum(scores), winRate(wins, loses), games));
		}
	}

	private static int sum(List<Integer> scores) {
		return scores.stream().mapToInt(Integer::intValue).sum();
	}

	private static double winRate(int wins, int loses) {
		int games = wins + loses;
		return games == 0 ? 0.0 : wins * 100.0 / games;
	}

	private static String rankMark(int rank) {
		switch (rank) {
		case 1: return "🥇";
		case 2: return "🥈";
		case 3: return "🥉";
		default: return rank + ".";
		}
	}

	public String formatDate2(Object raw) {
		LocalDate date = LocalDate.parse(raw.toString(), YYYYMMDD);
		String formatted = date.getYear() + "/" + date.getMonthValue() + "/" + date.getDayOfMonth();
		return formatted; // 輸出：2025/3/7
	}

	public String showAllRounds() {
		List<Map<String, Object>> rows = jdbc
				.queryForList("SELECT round_date,player,score FROM mahjong_records ORDER BY round_date ASC,player ASC");
		if (rows.isEmpty())
			return "目前沒有任何戰績記錄。";
		StringBuilder sb = new StringBuilder("📅 所有戰績：\n");
		String cur = "";
		StringBuilder line = new StringBuilder();
		for (Map<String, Object> r : rows) {
			String d = (String) r.get("round_date");
			String p = (String) r.get("player");
			p = rename(p);
			int s = ((Number) r.get("score")).intValue();
			if (!d.equals(cur)) {
				if (!cur.isEmpty()) {
					sb.append(cur).append("：").append(line.toString().replaceAll(", $", "")).append("\n");
					line.setLength(0);
				}
				cur = d;
			}
			line.append(String.format("%s %+,d, ", p, s));
		}
		if (!cur.isEmpty())
			sb.append(cur).append("：").append(line.toString().replaceAll(", $", "")).append("");
		return sb.toString().trim();
	}

	private String rename(String p) {
		if (p.equalsIgnoreCase("蕭")) {
			return "蕭先生";
		} else if (p.equalsIgnoreCase("隨")) {
			return "隨緣";
		} else if (p.equalsIgnoreCase("鹹")) {
			return "鹹蛋";
		} else if (p.equalsIgnoreCase("堂")) {
			return "陳堂弟";
		} else if (p.equalsIgnoreCase("馬") || p.equalsIgnoreCase("快")) {
			return "快馬";
		} else if (p.equalsIgnoreCase("肥") || p.equalsIgnoreCase("懶")) {
			return "懶肥";
		} else if (p.equalsIgnoreCase("鳥")) {
			return "阿鳥";
		}
		return p;
	}

	private boolean isValidDate(String date) {
		try {
			// uuuuMMdd + STRICT 才會擋掉 20250230 這類不存在的日期
			LocalDate.parse(date, DateTimeFormatter.ofPattern("uuuuMMdd")
					.withResolverStyle(java.time.format.ResolverStyle.STRICT));
			return true;
		} catch (DateTimeParseException e) {
			return false;
		}
	}

	private String formatDate(Object object) {
		return object.toString().substring(0, 4) + "/" + object.toString().substring(4, 6) + "/" + object.toString().substring(6, 8);
	}
}
