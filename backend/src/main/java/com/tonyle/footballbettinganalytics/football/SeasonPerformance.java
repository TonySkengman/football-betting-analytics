package com.tonyle.footballbettinganalytics.football;

import java.time.Instant;
import java.util.List;
import static com.tonyle.footballbettinganalytics.football.RecentPerformance.*;

public final class SeasonPerformance {
    private SeasonPerformance() {}

    public record Record(int played, int excluded, int wins, int draws, int losses,
                         Integer goalsScored, Integer goalsConceded,
                         Double averageScored, Double averageConceded) {}
    public record TeamSeason(long teamId, Record overall, Record home, Record away) {}
    public record CompetitionAverage(int played, int excluded, Double averageHomeGoals, Double averageAwayGoals) {}
    public record Response(TeamSeason home, TeamSeason away, CompetitionAverage competition) {}

    // The upstream request is scoped to the selected competition and season. Check
    // metadata when supplied as well, so unrelated fixtures cannot enter a summary.
    public static List<SourceMatch> eligible(SourceMatch selected, List<SourceMatch> fixtures) {
        Instant cutoff = RecentPerformance.timestamp(selected.utcDate());
        if (cutoff == null) return List.of();
        return fixtures.stream().filter(match -> match != null && match.id() != selected.id())
                .filter(match -> "FINISHED".equals(match.status()))
                .filter(match -> RecentPerformance.timestamp(match.utcDate()) != null
                        && RecentPerformance.timestamp(match.utcDate()).isBefore(cutoff))
                .filter(match -> match.homeTeam() != null && match.awayTeam() != null)
                .filter(match -> match.competition() == null || selected.competition().code().equals(match.competition().code()))
                .filter(match -> match.season() == null || selected.season().startDate().equals(match.season().startDate()))
                .toList();
    }

    public static Response calculate(SourceMatch selected, List<SourceMatch> eligible) {
        var scored = eligible.stream().filter(SeasonPerformance::hasScore).toList();
        int homeGoals = 0, awayGoals = 0;
        for (var match : scored) {
            homeGoals += match.score().fullTime().home();
            awayGoals += match.score().fullTime().away();
        }
        return new Response(team(selected.homeTeam().id(), eligible), team(selected.awayTeam().id(), eligible),
                new CompetitionAverage(scored.size(), eligible.size() - scored.size(),
                        average(homeGoals, scored.size()), average(awayGoals, scored.size())));
    }

    private static TeamSeason team(long id, List<SourceMatch> fixtures) {
        var home = fixtures.stream().filter(match -> match.homeTeam().id() == id).toList();
        var away = fixtures.stream().filter(match -> match.awayTeam().id() == id).toList();
        var overall = fixtures.stream().filter(match -> match.homeTeam().id() == id || match.awayTeam().id() == id).toList();
        return new TeamSeason(id, summarize(id, overall), summarize(id, home), summarize(id, away));
    }

    private static Record summarize(long teamId, List<SourceMatch> fixtures) {
        int played = 0, wins = 0, draws = 0, losses = 0, goals = 0, conceded = 0;
        for (var match : fixtures) {
            if (!hasScore(match)) continue;
            boolean home = match.homeTeam().id() == teamId;
            var score = match.score().fullTime();
            int scored = home ? score.home() : score.away();
            int against = home ? score.away() : score.home();
            played++;
            goals += scored;
            conceded += against;
            if (scored > against) wins++;
            else if (scored == against) draws++;
            else losses++;
        }
        return new Record(played, fixtures.size() - played, wins, draws, losses,
                played == 0 ? null : goals, played == 0 ? null : conceded,
                average(goals, played), average(conceded, played));
    }

    private static boolean hasScore(SourceMatch match) {
        var score = match.score() == null ? null : match.score().fullTime();
        return score != null && score.home() != null && score.away() != null && score.home() >= 0 && score.away() >= 0;
    }

    private static Double average(int goals, int count) {
        return count == 0 ? null : (double) goals / count;
    }
}
