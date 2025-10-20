package com.example.liushiudmjlinebot.util;

public final class StatsCalculator {
    private StatsCalculator(){}

    public static double stddev(long sum, long sumSq, int n) {
        if (n <= 0) return 0.0;
        double avg = (double) sum / n;
        double meanSq = (double) sumSq / n;
        double variance = Math.max(0.0, meanSq - avg * avg);
        return Math.sqrt(variance);
    }
}
