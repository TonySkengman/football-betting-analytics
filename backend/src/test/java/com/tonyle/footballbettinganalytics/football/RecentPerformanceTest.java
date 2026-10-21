package com.tonyle.footballbettinganalytics.football;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.tonyle.footballbettinganalytics.football.FootballDtos.*;
import static com.tonyle.footballbettinganalytics.football.RecentPerformance.*;

class RecentPerformanceTest {
    private final ApiTeam team = new ApiTeam(1, "Team", "ONE", null);
    private final ApiTeam opponent = new ApiTeam(2, "Opponent", "TWO", null);
    private final Instant cutoff = Instant.parse("2026-10-21T15:00:00Z");

    private SourceMatch match(long id, String date, String status, boolean home, Integer homeScore, Integer awayScore) {
        return new SourceMatch(id, date, status, new Competition("PL", "Premier League"), new Season("2026-08-01"),
                home ? team : opponent, home ? opponent : team, new ApiScore(new ApiScoreline(homeScore, awayScore)));
    }

    @Test
    void ordersNewestFirstLimitsFiveAndExcludesCutoffAndLaterResults() {
        var sources = new java.util.ArrayList<>(IntStream.rangeClosed(1, 7)
                .mapToObj(day -> match(day, "2026-10-%02dT15:00:00Z".formatted(day), "FINISHED", true, 1, 0)).toList());
        sources.add(match(8, "2026-10-21T15:00:00Z", "FINISHED", true, 9, 0));
        sources.add(match(9, "2026-10-22T15:00:00Z", "FINISHED", true, 9, 0));
        sources.add(match(10, "2026-10-20T15:00:00Z", "SCHEDULED", true, 9, 0));
        sources.add(match(11, "invalid", "FINISHED", true, 9, 0));
        var result = calculate(team, 99, cutoff, sources);
        assertEquals(List.of(7L, 6L, 5L, 4L, 3L), result.results().stream().map(Result::id).toList());
        assertEquals(5, result.summary().wins());
    }

    @Test
    void handlesHomeAwayPerspectiveAndCalculatesOnlyKnownScores() {
        var result = calculate(team, 99, cutoff, List.of(
                match(1, "2026-10-20T15:00:00Z", "FINISHED", true, 2, 0),
                match(2, "2026-10-19T15:00:00Z", "FINISHED", false, 3, 1),
                match(3, "2026-10-18T15:00:00Z", "FINISHED", false, 0, 0),
                match(4, "2026-10-17T15:00:00Z", "FINISHED", true, null, null)));
        assertEquals(List.of("W", "L", "D"), result.results().subList(0, 3).stream().map(Result::outcome).toList());
        assertEquals("Away", result.results().get(1).venue());
        assertEquals(1, result.results().get(1).scored());
        assertEquals(3, result.results().get(1).conceded());
        assertEquals(2, result.results().get(1).opponent().id());
        var summary = result.summary();
        assertEquals(4, summary.matches());
        assertEquals(3, summary.scoredMatches());
        assertEquals(1, summary.wins());
        assertEquals(1, summary.draws());
        assertEquals(1, summary.losses());
        assertEquals(3, summary.goalsScored());
        assertEquals(3, summary.goalsConceded());
        assertEquals(1.0, summary.averageScored());
        assertEquals(1.0, summary.averageConceded());
        assertNull(result.results().get(3).outcome());
    }

    @Test
    void emptyResultsHaveNoInventedGoalStatisticsAndExcludeSelectedMatch() {
        var result = calculate(team, 1, cutoff, List.of(match(1, "2026-10-01T15:00:00Z", "FINISHED", true, 1, 0)));
        assertTrue(result.results().isEmpty());
        assertEquals(0, result.summary().matches());
        assertNull(result.summary().goalsScored());
        assertNull(result.summary().averageScored());
    }
}
