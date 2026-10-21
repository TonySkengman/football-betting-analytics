package com.tonyle.footballbettinganalytics.football;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import static com.tonyle.footballbettinganalytics.football.FootballDtos.*;

public final class RecentPerformance {
    private RecentPerformance() {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record Competition(String code, String name) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record Season(String startDate) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record SourceMatch(long id, String utcDate, String status, Competition competition,
                              Season season, ApiTeam homeTeam, ApiTeam awayTeam, ApiScore score) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record SourceMatches(List<SourceMatch> matches) {}
    public record Result(long id, String utcDate, Team opponent, String venue,
                         Integer scored, Integer conceded, String outcome) {}
    public record Summary(int matches, int scoredMatches, int wins, int draws, int losses,
                          Integer goalsScored, Integer goalsConceded, Double averageScored, Double averageConceded) {}
    public record TeamPerformance(Team team, List<Result> results, Summary summary) {}
    public record Response(String competition, String season, String cutoff,
                           TeamPerformance home, TeamPerformance away, SeasonPerformance.Response seasonPerformance, PoissonModel.Estimate prediction) {}

    public static TeamPerformance calculate(ApiTeam team, long selectedId, Instant cutoff, List<SourceMatch> matches) {
        var results = matches.stream()
                .filter(match -> match.id() != selectedId && "FINISHED".equals(match.status()))
                .filter(match -> match.homeTeam() != null && match.awayTeam() != null)
                .filter(match -> match.homeTeam().id() == team.id() || match.awayTeam().id() == team.id())
                .filter(match -> timestamp(match.utcDate()) != null && timestamp(match.utcDate()).isBefore(cutoff))
                .sorted(Comparator.comparing((SourceMatch match) -> timestamp(match.utcDate())).reversed()
                        .thenComparingLong(SourceMatch::id))
                .limit(5)
                .map(match -> result(team.id(), match)).toList();
        int wins = 0, draws = 0, losses = 0, scoredMatches = 0, scored = 0, conceded = 0;
        for (var result : results) {
            if (result.scored() == null || result.conceded() == null) continue;
            scoredMatches++;
            scored += result.scored();
            conceded += result.conceded();
            switch (result.outcome()) {
                case "W" -> wins++;
                case "D" -> draws++;
                case "L" -> losses++;
                default -> { }
            }
        }
        return new TeamPerformance(team(team), results, new Summary(results.size(), scoredMatches, wins, draws, losses,
                scoredMatches == 0 ? null : scored, scoredMatches == 0 ? null : conceded,
                scoredMatches == 0 ? null : (double) scored / scoredMatches,
                scoredMatches == 0 ? null : (double) conceded / scoredMatches));
    }

    private static Result result(long teamId, SourceMatch match) {
        boolean home = match.homeTeam().id() == teamId;
        var score = match.score() == null ? null : match.score().fullTime();
        Integer scored = score == null ? null : home ? score.home() : score.away();
        Integer conceded = score == null ? null : home ? score.away() : score.home();
        String outcome = scored == null || conceded == null ? null : scored > conceded ? "W" : scored.equals(conceded) ? "D" : "L";
        return new Result(match.id(), match.utcDate(), team(home ? match.awayTeam() : match.homeTeam()),
                home ? "Home" : "Away", scored, conceded, outcome);
    }

    private static Team team(ApiTeam source) {
        return new Team(source.id(), source.name(), source.tla(), source.crest());
    }

    static Instant timestamp(String value) {
        if (value == null) return null;
        try { return Instant.parse(value); } catch (DateTimeParseException exception) { return null; }
    }
}
