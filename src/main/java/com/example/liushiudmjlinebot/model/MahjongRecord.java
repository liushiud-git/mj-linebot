package com.example.liushiudmjlinebot.model;

public class MahjongRecord {
    private Long id;
    private Long roundId;
    private String datetime; // ISO8601 string
    private String player;
    private int score;

    public MahjongRecord(Long id, Long roundId, String datetime, String player, int score) {
        this.id = id;
        this.roundId = roundId;
        this.datetime = datetime;
        this.player = player;
        this.score = score;
    }

    public Long getId() { return id; }
    public Long getRoundId() { return roundId; }
    public String getDatetime() { return datetime; }
    public String getPlayer() { return player; }
    public int getScore() { return score; }
}
