package com.tonyle.footballbettinganalytics.football;

import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.tonyle.footballbettinganalytics.football.RecentPerformance.*;

final class BacktestEvaluation {
    static final int MIN_BASELINE = 30;
    record Outcomes(int result, int goals, int btts) {}
    record Prediction(double[] result, double[] goals, double[] btts) {}
    static final String CANDIDATE_VERSION = "equal-blend-experiment-v1";
    record Comparison(BacktestMetrics.Summary model, BacktestMetrics.Summary baseline, BacktestMetrics.Summary candidate) {}
    record Case(long matchId, String kickoff, Outcomes actual, Prediction model, Prediction baseline) {}
    record Report(String modelVersion, int selected, int modelAvailable, int compared,
                  Map<String, Integer> skipped, Map<String, Comparison> markets, List<Long> comparedMatchIds, String candidateVersion, List<Case> cases) {}

    static Outcomes outcomes(SourceMatch match) {
        var score = PoissonModel.regulationScore(match.score());
        if (score == null) return null;
        return new Outcomes(score.home() > score.away() ? 0 : score.home().equals(score.away()) ? 1 : 2,
                score.home() + score.away() > 2 ? 0 : 1, score.home() > 0 && score.away() > 0 ? 0 : 1);
    }

    static List<SourceMatch> eligibleBaseline(SourceMatch selected, List<SourceMatch> fixtures) {
        var cutoff = timestamp(selected.utcDate());
        var start = cutoff.minus(365, ChronoUnit.DAYS);
        return fixtures.stream().filter(m -> m != null && m.id() != selected.id() && "FINISHED".equals(m.status()))
                .filter(m -> m.competition() != null && selected.competition().code().equals(m.competition().code()))
                .filter(m -> timestamp(m.utcDate()) != null && timestamp(m.utcDate()).isBefore(cutoff) && !timestamp(m.utcDate()).isBefore(start))
                .filter(m -> outcomes(m) != null).toList();
    }

    static Prediction baseline(SourceMatch selected, List<SourceMatch> fixtures) {
        var eligible = eligibleBaseline(selected, fixtures);
        if (eligible.size() < MIN_BASELINE) return null;
        double[] result = new double[3], goals = new double[2], btts = new double[2];
        for (var match : eligible) {
            var actual = outcomes(match);
            result[actual.result()]++; goals[actual.goals()]++; btts[actual.btts()]++;
        }
        for (var market : List.of(result, goals, btts)) for (int i = 0; i < market.length; i++) market[i] /= eligible.size();
        return new Prediction(result, goals, btts);
    }

    static double[] blend(double[] model, double[] baseline) {
        if (model.length != baseline.length) throw new IllegalArgumentException("Market sizes must match.");
        BacktestMetrics.score(model, 0);
        BacktestMetrics.score(baseline, 0);
        double[] result = new double[model.length];
        for (int i = 0; i < result.length; i++) result[i] = (model[i] + baseline[i]) / 2;
        return result;
    }

    static Report evaluate(List<SourceMatch> selected, List<SourceMatch> competitionHistory,
                           Map<Long, TeamHistory.Loaded> histories, String baselineError) {
        var model = List.of(new BacktestMetrics(), new BacktestMetrics(), new BacktestMetrics());
        var candidate = List.of(new BacktestMetrics(), new BacktestMetrics(), new BacktestMetrics());
        var cases = new ArrayList<Case>();
        var reference = List.of(new BacktestMetrics(), new BacktestMetrics(), new BacktestMetrics());
        var skipped = new TreeMap<String, Integer>();
        int available = 0, compared = 0;
        var comparedIds = new ArrayList<Long>();
        for (var match : selected) {
            String reason = null;
            var actual = outcomes(match);
            if (actual == null) reason = "unconfirmed_regulation_score";
            else if (match.homeTeam() == null || match.awayTeam() == null || timestamp(match.utcDate()) == null) reason = "invalid_metadata";
            if (reason != null) { skipped.merge(reason, 1, Integer::sum); continue; }
            var missing = TeamHistory.Loaded.failed("History not fetched or request budget exhausted.");
            var estimate = PoissonModel.estimate(match, histories.getOrDefault(match.homeTeam().id(), missing), histories.getOrDefault(match.awayTeam().id(), missing));
            if (!"available".equals(estimate.status())) { skipped.merge(estimate.status(), 1, Integer::sum); continue; }
            available++;
            var baseline = baselineError == null ? baseline(match, competitionHistory) : null;
            if (baseline == null) { skipped.merge(baselineError == null ? "baseline_insufficient_data" : "baseline_unavailable", 1, Integer::sum); continue; }
            var p = estimate.probabilities();
            var prediction = new Prediction(new double[]{p.homeWin(), p.draw(), p.awayWin()}, new double[]{p.over25(), p.under25()}, new double[]{p.bttsYes(), p.bttsNo()});
            var predicted = List.of(prediction.result(), prediction.goals(), prediction.btts());
            var baselines = List.of(baseline.result(), baseline.goals(), baseline.btts());
            int[] labels = {actual.result(), actual.goals(), actual.btts()};
            for (int i = 0; i < 3; i++) {
                model.get(i).add(predicted.get(i), labels[i]);
                reference.get(i).add(baselines.get(i), labels[i]);
                candidate.get(i).add(blend(predicted.get(i), baselines.get(i)), labels[i]);
            }
            cases.add(new Case(match.id(), match.utcDate(), actual, prediction, baseline));
            compared++;
            comparedIds.add(match.id());
        }
        var markets = new LinkedHashMap<String, Comparison>();
        var names = List.of("1X2", "Over/Under 2.5", "BTTS");
        for (int i = 0; i < 3; i++) markets.put(names.get(i), new Comparison(model.get(i).summary(), reference.get(i).summary(), candidate.get(i).summary()));
        return new Report(PoissonModel.VERSION, selected.size(), available, compared, skipped, markets, List.copyOf(comparedIds), CANDIDATE_VERSION, List.copyOf(cases));
    }
}
