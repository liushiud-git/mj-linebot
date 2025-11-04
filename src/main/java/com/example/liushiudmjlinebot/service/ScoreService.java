package com.example.liushiudmjlinebot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.*;

@Service
public class ScoreService {

	private final JdbcTemplate jdbc;
	private static final Logger log = LoggerFactory.getLogger(ScoreService.class);

	public ScoreService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	private static final Pattern LINE_PATTERN = Pattern.compile("^(?<date>\\d{8})\\s*戰績[:：]\\s*(?<pairs>.+)$");

	// @Transactional
	public String addByFormattedLine(String text) {
		try {
			Matcher m = LINE_PATTERN.matcher(text.trim());
			if (!m.matches())
				return "❌ 格式錯誤，請用：20251017 戰績：隨 -7700,蕭 -2100,馬 5700,堂 3700,鳥 400";
			String date = m.group("date");
			String pairs = m.group("pairs");

			log.info("date = " + date);
			log.info("pairs = " + pairs);

			deleteByDate(date);

			String sql = String.format("INSERT INTO mahjong_rounds(round_date) VALUES ('%s')", date);
			jdbc.execute(sql);

			Long roundId = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);

			int inserted = 0;
			StringBuilder msg = new StringBuilder();
			for (String seg : pairs.split("\s*,\s*")) {
				String[] kv = seg.trim().split("\s+");
				if (kv.length != 2)
					continue;
				String p = kv[0];
				log.info("name = " + p);
				p = rename(p);
				int s;
				try {
					s = Integer.parseInt(kv[1]);
					log.info("value = " + s);
				} catch (Exception e) {
					continue;
				}

				sql = String.format(
						"INSERT INTO mahjong_records(round_id,round_date,player,score) VALUES (%d,'%s','%s','%s')",
						roundId, date, p, s);
				jdbc.execute(sql);

				msg.append(String.format("%s %+d (%s)\n", p, s, s > 0 ? "1勝0敗" : s < 0 ? "0勝1敗" : "0勝0敗"));
				inserted++;
			}
			if (inserted == 0)
				return "❌ 未寫入任何分數";
			recomputeSummary();
			return "✅ 已登錄 " + formatDate(date) + " 戰績\n" + msg.toString().trim();

		} catch (Exception ex) {
			ex.printStackTrace();
			return "哎啊~新增有問題";
		}

	}

	public String deleteByDateCommand(String text) {
		String date = text.replaceAll("[^\\d]", "");
		if (date.length() != 8)
			return "❌ 請提供 yyyyMMdd 日期";
		int r = deleteByDate(date);
		recomputeSummary();
		return r == 0 ? "ℹ️ 該日期無資料" : "🗑 已刪除 " + date + " 戰績";
	}

	private int deleteByDate(String date) {
		List<Long> ids = jdbc.queryForList("SELECT id FROM mahjong_rounds WHERE round_date=?", Long.class, date);
		int cnt = 0;
		for (Long id : ids) {
			cnt += jdbc.update("DELETE FROM mahjong_records WHERE round_id=" + id);
			cnt += jdbc.update("DELETE FROM mahjong_rounds WHERE id=" + id);
		}
		return cnt;
	}

	public String status() {
		List<Map<String, Object>> rows = jdbc
				.queryForList("SELECT player,SUM(score) total," + "SUM(CASE WHEN score>0 THEN 1 ELSE 0 END) wins,"
						+ "SUM(CASE WHEN score<0 THEN 1 ELSE 0 END) loses " + "FROM mahjong_records GROUP BY player");
		if (rows.isEmpty())
			return "目前沒有任何戰績。";
		rows.sort(
				(a, b) -> Integer.compare(((Number) b.get("total")).intValue(), ((Number) a.get("total")).intValue()));
		StringBuilder sb = new StringBuilder("📊 目前總戰績：\n");
		for (Map<String, Object> r : rows) {

			String name = (String) r.get("player");
			int total = ((Number) r.get("total")).intValue();
			int wins = ((Number) r.get("wins")).intValue();
			int loses = ((Number) r.get("loses")).intValue();
			int totalGames = wins + loses;
			double winRate = totalGames == 0 ? 0.0 : (wins * 100.0 / totalGames);

			sb.append(String.format("%-4s %,6d (%d勝%d敗) %6.1f%%\n", name, total, wins, loses, winRate));

			// sb.append(String.format("%-4s %,6d (%d勝%d敗)\n", r.get("player"),
			// r.get("total"), r.get("wins"), r.get("loses")));
		}

		Map<String, Object> topWin = jdbc.queryForMap("SELECT round_date, player, score FROM mahjong_records "
				+ "WHERE score = (SELECT MAX(score) FROM mahjong_records)");
		
		Map<String, Object> topLose = jdbc.queryForMap("SELECT round_date, player, score FROM mahjong_records "
				+ "WHERE score = (SELECT MIN(score) FROM mahjong_records)");

		sb.append("\n🏆 單場勝最多：").append(String.format("%s %+d（%s）", topWin.get("player"),
				((Number) topWin.get("score")).intValue(), topWin.get("round_date")));

		sb.append("\n💀 單場輸最多：").append(String.format("%s %+d（%s）", topLose.get("player"),
				((Number) topLose.get("score")).intValue(), topLose.get("round_date")));

		return sb.toString().trim();
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

	private void recomputeSummary() {
		/* dummy for compatibility */ }

	private String formatDate(String d) {
		return d.substring(0, 4) + "/" + d.substring(4, 6) + "/" + d.substring(6, 8);
	}
}
