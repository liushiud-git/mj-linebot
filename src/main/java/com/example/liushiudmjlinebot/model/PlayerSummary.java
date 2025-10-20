package com.example.liushiudmjlinebot.model;

public class PlayerSummary {
    private String player;
    private int totalScore;
    private double stddev;

    public PlayerSummary(String player, int totalScore, double stddev) {
        this.player = player;
        this.totalScore = totalScore;
        this.stddev = stddev;
    }

    public String getPlayer() { return player; }
    public int getTotalScore() { return totalScore; }
    public double getStddev() { return stddev; }
}
