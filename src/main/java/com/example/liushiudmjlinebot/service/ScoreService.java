package com.example.liushiudmjlinebot.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.regex.*;

@Service
public class ScoreService {
	private final JdbcTemplate jdbc;

	public ScoreService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	private static final Pattern LINE_PATTERN = Pattern.compile("^(?<date>\\d{8})\\s*戰績[:：]\\s*(?<pairs>.+)$");

	@Transactional
	public String addByFormattedLine(String text) {
		try {
			Matcher m = LINE_PATTERN.matcher(text.trim());
			if (!m.matches())
				return "❌ 格式錯誤，請用：20251017 戰績：隨 -7700,蕭 -2100,馬 5700,堂 3700,鳥 400";
			String date = m.group("date");
			String pairs = m.group("pairs");
			
			System.out.println("date = " + date);
			System.out.println("pairs = " + pairs);

			deleteByDate(date);
			jdbc.update("INSERT INTO mahjong_rounds(date) VALUES (?)", date);
			Long roundId = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);

			int inserted = 0;
			StringBuilder msg = new StringBuilder();
			for (String seg : pairs.split("\s*,\s*")) {
				String[] kv = seg.trim().split("\s+");
				if (kv.length != 2)
					continue;
				String p = kv[0];
				System.out.println("name = " + p);
				int s;
				try {
					s = Integer.parseInt(kv[1]);
					System.out.println("value = " + s);
				} catch (Exception e) {
					continue;
				}
				jdbc.update("INSERT INTO mahjong_records(round_id,date,player,score) VALUES (?,?,?,?)", roundId, date, p,
						s);
				msg.append(String.format("%s %+d (%s)\n", p, s, s > 0 ? "1勝0敗" : s < 0 ? "0勝1敗" : "0勝0敗"));
				inserted++;
			}
			if (inserted == 0)
				return "❌ 未寫入任何分數";
			recomputeSummary();
			return "✅ 已登錄 " + formatDate(date) + " 戰績\n" + msg.toString().trim();
			
		}catch(Exception ex) {
			System.out.println("exception " + ex.getMessage());
			return "哎啊~新增有問題";
		}
		
	}

	@Transactional
	public String deleteByDateCommand(String text) {
		String date = text.replaceAll("[^\\d]", "");
		if (date.length() != 8)
			return "❌ 請提供 yyyyMMdd 日期";
		int r = deleteByDate(date);
		recomputeSummary();
		return r == 0 ? "ℹ️ 該日期無資料" : "🗑 已刪除 " + date + " 戰績";
	}

	private int deleteByDate(String date) {
		List<Long> ids = jdbc.queryForList("SELECT id FROM mahjong_rounds WHERE date=?", Long.class, date);
		int cnt = 0;
		for (Long id : ids) {
			cnt += jdbc.update("DELETE FROM mahjong_records WHERE round_id=?", id);
			cnt += jdbc.update("DELETE FROM mahjong_rounds WHERE id=?", id);
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
			sb.append(
					String.format("%s %+d (%d勝%d敗)\n", r.get("player"), r.get("total"), r.get("wins"), r.get("loses")));
		}
		return sb.toString().trim();
	}

	public String showAllRounds() {
		List<Map<String, Object>> rows = jdbc
				.queryForList("SELECT date,player,score FROM mahjong_records ORDER BY date ASC,player ASC");
		if (rows.isEmpty())
			return "目前沒有任何戰績記錄。";
		StringBuilder sb = new StringBuilder("📅 所有戰績：\n");
		String cur = "";
		StringBuilder line = new StringBuilder();
		for (Map<String, Object> r : rows) {
			String d = (String) r.get("date");
			String p = (String) r.get("player");
			int s = ((Number) r.get("score")).intValue();
			if (!d.equals(cur)) {
				if (!cur.isEmpty()) {
					sb.append(cur).append("：").append(line.toString().replaceAll(", $", "")).append("\n");
					line.setLength(0);
				}
				cur = d;
			}
			line.append(String.format("%s %+d, ", p, s));
		}
		if (!cur.isEmpty())
			sb.append(cur).append("：").append(line.toString().replaceAll(", $", "")).append("");
		return sb.toString().trim();
	}

	private void recomputeSummary() {
		/* dummy for compatibility */ }

	private String formatDate(String d) {
		return d.substring(0, 4) + "/" + d.substring(4, 6) + "/" + d.substring(6, 8);
	}
}
