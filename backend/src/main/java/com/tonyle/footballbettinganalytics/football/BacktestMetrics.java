package com.tonyle.footballbettinganalytics.football;

import java.util.ArrayList;
import java.util.List;

final class BacktestMetrics {
    static final double EPSILON = 1e-15;
    record Score(double brier, double logLoss) {}
    record Bin(double from, double to, int count, Double meanProbability, Double actualFrequency, boolean sparse) {}
    record Summary(int matches, Double brier, Double logLoss, List<Bin> calibration) {}
    private double brier, logLoss;
    private int matches;
    private final int[] counts = new int[10], hits = new int[10];
    private final double[] probabilities = new double[10];

    static Score score(double[] probabilities, int actual) {
        if (actual < 0 || actual >= probabilities.length) throw new IllegalArgumentException("Invalid outcome.");
        double sum = 0, brier = 0;
        for (int i = 0; i < probabilities.length; i++) {
            double p = probabilities[i];
            if (!Double.isFinite(p) || p < 0 || p > 1) throw new IllegalArgumentException("Invalid probability.");
            sum += p;
            brier += Math.pow(p - (i == actual ? 1 : 0), 2);
        }
        if (Math.abs(sum - 1) > 1e-9) throw new IllegalArgumentException("Probabilities must sum to one.");
        // Binary Brier uses just the positive outcome; multiclass uses the sum over classes.
        if (probabilities.length == 2) brier /= 2;
        return new Score(brier, Math.max(0, -Math.log(Math.max(EPSILON, probabilities[actual]))));
    }

    void add(double[] prediction, int actual) {
        var score = score(prediction, actual);
        matches++; brier += score.brier(); logLoss += score.logLoss();
        // For 1X2, pool all three outcome events. For binary markets use Yes/Over only.
        int events = prediction.length == 2 ? 1 : prediction.length;
        for (int i = 0; i < events; i++) {
            int bin = Math.min(9, (int) Math.floor(prediction[i] * 10));
            counts[bin]++; probabilities[bin] += prediction[i];
            if (i == actual) hits[bin]++;
        }
    }

    Summary summary() {
        var bins = new ArrayList<Bin>();
        for (int i = 0; i < 10; i++) bins.add(new Bin(i / 10.0, (i + 1) / 10.0, counts[i],
                counts[i] == 0 ? null : probabilities[i] / counts[i],
                counts[i] == 0 ? null : (double) hits[i] / counts[i], counts[i] < 20));
        return new Summary(matches, matches == 0 ? null : brier / matches, matches == 0 ? null : logLoss / matches, List.copyOf(bins));
    }
}
