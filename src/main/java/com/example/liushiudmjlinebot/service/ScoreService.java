package com.example.liushiudmjlinebot.service;

import com.example.liushiudmjlinebot.model.PlayerSummary;
import com.example.liushiudmjlinebot.util.StatsCalculator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ScoreService {
    private final JdbcTemplate jdbc;
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final Pattern DATE_PREFIX = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}.*");

    public ScoreService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public String addRound(String input) {
        // 支援兩種格式：
        // /add A +2000 B -1500 C -200 D -300
        // /add 2025-10-19T20:01 A +2000 B -1500 ...
        String[] tokens = input.trim().split("\\s+");
        if (tokens.length < 3) return "❌ 格式錯誤，請輸入：/add A +2000 B -1500 ...";

        int idx = 1;
        String datetime;
        if (tokens.length > 3 && DATE_PREFIX.matcher(tokens[1]).matches()) {
            // 使用者有提供時間
            datetime = normalizeDatetime(tokens[1]);
            idx = 2;
        } else {
            datetime = ZonedDateTime.now(TAIPEI).format(ISO);
        }

        if ((tokens.length - idx) % 2 != 0) {
            return "❌ 格式錯誤，玩家與分數需成對：/add A +2000 B -1500";
        }

        // 建立回合
        jdbc.update("INSERT INTO mahjong_rounds(datetime) VALUES (?)", datetime);
        Long roundId = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);

        // 插入每人分數
        for (int i = idx; i < tokens.length; i += 2) {
            String name = tokens[i];
            int delta;
            try {
                delta = Integer.parseInt(tokens[i+1]);
            } catch (NumberFormatException ex) {
                return "❌ 分數必須為整數，例如 +2000 或 -300";
            }
            jdbc.update("INSERT INTO mahjong_records(round_id, datetime, player, score) VALUES (?, ?, ?, ?)",
                    roundId, datetime, name, delta);
        }

        // 更新 summary
        recomputeSummary();

        // 回覆最新統計
        return status();
    }

    public String status() {
        List<PlayerSummary> list = jdbc.query("SELECT player,SUM(score) AS total, SUM(score*score) AS sumsq, COUNT(*) AS n FROM mahjong_records GROUP BY player",
        		rs -> {
            List<PlayerSummary> res = new ArrayList<>();
            while (rs.next()) {
                String p = rs.getString("player");
                int total = rs.getInt("total");
                long sumsq = rs.getLong("sumsq");
                int n = rs.getInt("n");
                double stddev = StatsCalculator.stddev(total, sumsq, n);
                res.add(new PlayerSummary(p, total, stddev));
            }
            return res;
        });

        if (list.isEmpty()) return "目前沒有任何戰績。";

        list.sort(Comparator.comparingInt(PlayerSummary::getTotalScore).reversed());

        StringBuilder sb = new StringBuilder("📊 麻將戰績統計\n");
        sb.append("───────────────\n");
        for (PlayerSummary ps : list) {
            sb.append(String.format("%s：總分 %d，標準差 %.1f\n", ps.getPlayer(), ps.getTotalScore(), ps.getStddev()));
        }
        return sb.toString();
    }

    public String show10() {
        // 取最近 10 個回合，顯示每回合各玩家成績
        List<Map<String, Object>> rounds = jdbc.queryForList("SELECT id, datetime FROM mahjong_rounds ORDER BY id DESC LIMIT 10 ");

        if (rounds.isEmpty()) return "目前沒有任何戰績。";

        StringBuilder sb = new StringBuilder("🀄 近期 10 場戰績：\n");
        for (Map<String,Object> r : rounds) {
            long roundId = ((Number)r.get("id")).longValue();
            String dt = (String) r.get("datetime");
            sb.append(dt).append("\n  ");
            List<Map<String, Object>> recs = jdbc.queryForList("SELECT player, score FROM mahjong_records WHERE round_id = ? ORDER BY player ASC", roundId);
            String line = recs.stream()
                    .map(m -> m.get("player") + " " + ( (Number)m.get("score")).intValue())
                    .collect(Collectors.joining(", "));
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    @Transactional
    public void recomputeSummary() {
        // 清空 summary
        jdbc.update("DELETE FROM mahjong_summary");
        // 重新計算
        jdbc.query("SELECT player, SUM(score) AS total, SUM(score*score) AS sumsq, COUNT(*) AS n FROM mahjong_records GROUP BY player", rs -> {
            while (rs.next()) {
                String p = rs.getString("player");
                int total = rs.getInt("total");
                long sumsq = rs.getLong("sumsq");
                int n = rs.getInt("n");
                double std = StatsCalculator.stddev(total, sumsq, n);
                jdbc.update("INSERT INTO mahjong_summary(player, total_score, stddev) VALUES (?,?,?)",
                        p, total, std);
            }
            return null;
        });
    }

    private String normalizeDatetime(String raw) {
        // 接受 YYYY-MM-DD 或 YYYY-MM-DDTHH:mm 或 完整 ISO
        try {
            if (raw.length() == 10) { // YYYY-MM-DD -> assume 00:00 Taipei
                ZonedDateTime zdt = ZonedDateTime.of(
                        Integer.parseInt(raw.substring(0,4)),
                        Integer.parseInt(raw.substring(5,7)),
                        Integer.parseInt(raw.substring(8,10)),
                        0,0,0,0, TAIPEI);
                return zdt.format(ISO);
            }
            // 若缺少時區，補上 +08:00
            if (!raw.contains("+") && !raw.endsWith("Z")) {
                // parse as local Taipei
                String t = raw;
                if (t.length()==16) t += ":00"; // add seconds if missing
                ZonedDateTime zdt = ZonedDateTime.of(
                        Integer.parseInt(t.substring(0,4)),
                        Integer.parseInt(t.substring(5,7)),
                        Integer.parseInt(t.substring(8,10)),
                        Integer.parseInt(t.substring(11,13)),
                        Integer.parseInt(t.substring(14,16)),
                        (t.length()>=19 ? Integer.parseInt(t.substring(17,19)) : 0),
                        0, TAIPEI);
                return zdt.format(ISO);
            }
            return ZonedDateTime.parse(raw).withZoneSameInstant(TAIPEI).format(ISO);
        } catch (Exception e) {
            return ZonedDateTime.now(TAIPEI).format(ISO);
        }
    }
}
