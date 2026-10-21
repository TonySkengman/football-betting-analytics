package com.tonyle.footballbettinganalytics.football;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import static com.tonyle.footballbettinganalytics.football.RecentPerformance.*;

public final class TeamHistory {
    public static final int TARGET = 20;
    public static final int LOOKBACK_DAYS = 365;
    public static final int REQUEST_LIMIT = 500;
    private TeamHistory() {}

    public record Loaded(List<SourceMatch> matches, String error) {
        public static Loaded failed(String error) { return new Loaded(List.of(), error); }
    }
    public record CompetitionCount(String code, String name, int matches) {}
    public record Provenance(long teamId, int target, int used, int homeMatches, int awayMatches,
                             int excludedScores, int excludedMetadata, int outsideWindow,
                             int duplicates, String firstDate, String lastDate,
                             List<CompetitionCount> competitions, String error) {}
    public record Prepared(List<SourceMatch> matches, Provenance provenance) {}

    public static Prepared prepare(long teamId, SourceMatch selected, Loaded loaded) {
        var cutoff = RecentPerformance.timestamp(selected.utcDate());
        var start = cutoff.minus(LOOKBACK_DAYS, ChronoUnit.DAYS);
        var unique = new LinkedHashMap<Long, SourceMatch>();
        int invalid = 0, scores = 0, outside = 0, duplicates = 0;
        for (var match : loaded.matches()) {
            if (match == null || match.id() <= 0 || match.homeTeam() == null || match.awayTeam() == null
                    || (match.homeTeam().id() != teamId && match.awayTeam().id() != teamId)
                    || RecentPerformance.timestamp(match.utcDate()) == null
                    || match.competition() == null || match.competition().code() == null
                    || match.competition().name() == null) { invalid++; continue; }
            Instant date = RecentPerformance.timestamp(match.utcDate());
            if (match.id() == selected.id() || !"FINISHED".equals(match.status())
                    || !date.isBefore(cutoff) || date.isBefore(start)) { outside++; continue; }
            if (unique.containsKey(match.id())) { duplicates++; continue; }
            unique.put(match.id(), match);
        }
        var scored = new java.util.ArrayList<SourceMatch>();
        for (var match : unique.values()) {
            if (PoissonModel.regulationScore(match.score()) == null) scores++;
            else scored.add(match);
        }
        var results = scored.stream().sorted(Comparator.comparing((SourceMatch match) -> RecentPerformance.timestamp(match.utcDate()))
                        .reversed().thenComparingLong(SourceMatch::id)).limit(TARGET).toList();
        var competitions = new java.util.TreeMap<String, CompetitionCount>();
        int home = 0;
        for (var match : results) {
            if (match.homeTeam().id() == teamId) home++;
            var previous = competitions.get(match.competition().code());
            competitions.put(match.competition().code(), new CompetitionCount(match.competition().code(),
                    match.competition().name(), previous == null ? 1 : previous.matches() + 1));
        }
        return new Prepared(results, new Provenance(teamId, TARGET, results.size(), home, results.size() - home,
                scores, invalid, outside, duplicates,
                results.isEmpty() ? null : results.getLast().utcDate(), results.isEmpty() ? null : results.getFirst().utcDate(),
                List.copyOf(competitions.values()), loaded.error()));
    }
}
