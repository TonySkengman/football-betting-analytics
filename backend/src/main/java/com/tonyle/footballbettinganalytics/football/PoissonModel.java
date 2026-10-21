package com.tonyle.footballbettinganalytics.football;

import java.util.List;
import static com.tonyle.footballbettinganalytics.football.FootballDtos.*;
import static com.tonyle.footballbettinganalytics.football.RecentPerformance.*;

public final class PoissonModel {
    public static final String VERSION = "cross-competition-poisson-v2";
    public static final int MIN_TEAM_MATCHES = 3;
    public static final int MIN_TOTAL_MATCHES = 8;
    private static final double TAIL_TOLERANCE = 1e-12;
    private static final double MAX_MEAN = 20;

    private PoissonModel() {}

    public record Samples(int homeMatches, int awayMatches, int homeTotal, int awayTotal,
                          int minimumVenueMatches, int minimumTotalMatches) {}
    public record Inputs(double homeScored, double homeConceded, double awayScored, double awayConceded) {}
    public record Probabilities(double homeWin, double draw, double awayWin,
                                double over25, double under25, double bttsYes, double bttsNo,
                                double omittedMass) {}
    public record Estimate(String modelVersion, String status, String reason, Samples samples,
                           Inputs inputs, Double homeExpectedGoals, Double awayExpectedGoals,
                           Probabilities probabilities, TeamHistory.Provenance homeHistory,
                           TeamHistory.Provenance awayHistory, List<String> warnings) {}

    public static Estimate estimate(SourceMatch selected, TeamHistory.Loaded homeSource, TeamHistory.Loaded awaySource) {
        var home = TeamHistory.prepare(selected.homeTeam().id(), selected, homeSource);
        var away = TeamHistory.prepare(selected.awayTeam().id(), selected, awaySource);
        var homeVenue = home.matches().stream().filter(match -> match.homeTeam().id() == selected.homeTeam().id()).toList();
        var awayVenue = away.matches().stream().filter(match -> match.awayTeam().id() == selected.awayTeam().id()).toList();
        var samples = new Samples(homeVenue.size(), awayVenue.size(), home.matches().size(), away.matches().size(), MIN_TEAM_MATCHES, MIN_TOTAL_MATCHES);
        var warnings = new java.util.ArrayList<String>();
        warnings.add("Only matches returned under your API subscription are included; coverage may be incomplete.");
        if (home.provenance().competitions().size() > 1 || away.provenance().competitions().size() > 1) {
            warnings.add("Mixed competitions: scoring levels and opponent strength are not adjusted.");
        }
        if (home.matches().size() < TeamHistory.TARGET || away.matches().size() < TeamHistory.TARGET) {
            warnings.add("Fewer than 20 eligible matches are available for at least one team in the 365-day window.");
        }
        if (homeVenue.size() < 5 || awayVenue.size() < 5) warnings.add("Small venue sample: fewer than five matches for at least one team.");
        String status = "available", reason = null;
        if (homeSource.error() != null || awaySource.error() != null) {
            status = "history_unavailable";
            reason = "One or both team histories could not be loaded completely. No probabilities were calculated.";
        } else if (home.matches().size() < MIN_TOTAL_MATCHES || away.matches().size() < MIN_TOTAL_MATCHES
                || homeVenue.size() < MIN_TEAM_MATCHES || awayVenue.size() < MIN_TEAM_MATCHES) {
            status = "insufficient_data";
            reason = "At least 8 eligible matches per team, including 3 home matches for the home team and 3 away matches for the away team, are required.";
        }
        if (!"available".equals(status)) return new Estimate(VERSION, status, reason, samples, null, null, null, null,
                home.provenance(), away.provenance(), List.copyOf(warnings));
        double hs = average(homeVenue, true), hc = average(homeVenue, false);
        double as = average(awayVenue, false), ac = average(awayVenue, true);
        // Blend measured attack and opponent concession rates directly. No unrelated league baseline is used.
        double homeMean = (hs + ac) / 2;
        double awayMean = (as + hc) / 2;
        var probabilities = probabilities(homeMean, awayMean);
        if (probabilities == null) return new Estimate(VERSION, "invalid_data", "Goal rates are outside the supported numerical range (0–20 per team).",
                samples, null, null, null, null, home.provenance(), away.provenance(), List.copyOf(warnings));
        return new Estimate(VERSION, "available", null, samples, new Inputs(hs, hc, as, ac), homeMean, awayMean,
                probabilities, home.provenance(), away.provenance(), List.copyOf(warnings));
    }

    private static double average(List<SourceMatch> fixtures, boolean homeScore) {
        return fixtures.stream().map(PoissonModel::score).mapToInt(value -> homeScore ? value.home() : value.away()).average().orElseThrow();
    }

    private static ApiScoreline score(SourceMatch match) { return regulationScore(match.score()); }

    static ApiScoreline regulationScore(ApiScore score) {
        if (score == null) return null;
        if (completeScore(score.regularTime())) return score.regularTime();
        // fullTime may include extra time and penalties. Never assume a missing duration means REGULAR.
        if ("REGULAR".equals(score.duration()) && completeScore(score.fullTime())) return score.fullTime();
        return null;
    }

    private static boolean completeScore(ApiScoreline score) {
        return score != null && score.home() != null && score.away() != null && score.home() >= 0 && score.away() >= 0;
    }

    private static boolean validRate(Double value) {
        return value != null && Double.isFinite(value) && value >= 0;
    }

    private static boolean validMean(double value) {
        return validRate(value) && value <= MAX_MEAN;
    }

    // Package visibility allows deterministic numerical tests without HTTP calls.
    static Probabilities probabilities(double homeMean, double awayMean) {
        if (!validMean(homeMean) || !validMean(awayMean)) return null;
        double[] home = distribution(homeMean);
        double[] away = distribution(awayMean);
        double homeWin = 0, draw = 0, awayWin = 0;
        for (int h = 0; h < home.length; h++) {
            for (int a = 0; a < away.length; a++) {
                double probability = home[h] * away[a];
                if (h > a) homeWin += probability;
                else if (h == a) draw += probability;
                else awayWin += probability;
            }
        }
        double mass = homeWin + draw + awayWin;
        double omitted = Math.max(0, 1 - mass);
        if (!Double.isFinite(mass) || mass <= 0 || omitted > 3 * TAIL_TOLERANCE) return null;
        // Condition 1X2 on the retained grid; report the tiny omitted mass explicitly.
        double totalMean = homeMean + awayMean;
        double under25 = Math.exp(-totalMean) * (1 + totalMean + totalMean * totalMean / 2);
        double yes = (-Math.expm1(-homeMean)) * (-Math.expm1(-awayMean));
        return new Probabilities(homeWin / mass, draw / mass, awayWin / mass,
                1 - under25, under25, yes, 1 - yes, omitted);
    }

    private static double[] distribution(double mean) {
        var values = new java.util.ArrayList<Double>();
        double probability = Math.exp(-mean);
        double sum = probability;
        values.add(probability);
        for (int goals = 1; 1 - sum > TAIL_TOLERANCE && goals <= 200; goals++) {
            probability *= mean / goals;
            values.add(probability);
            sum += probability;
        }
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }
}
