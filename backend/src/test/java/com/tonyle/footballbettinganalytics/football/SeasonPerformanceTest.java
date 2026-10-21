package com.tonyle.footballbettinganalytics.football;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.tonyle.footballbettinganalytics.football.FootballDtos.*;
import static com.tonyle.footballbettinganalytics.football.RecentPerformance.*;

class SeasonPerformanceTest {
    private final ApiTeam home = new ApiTeam(1, "Home", "HOM", null);
    private final ApiTeam away = new ApiTeam(2, "Away", "AWY", null);
    private final ApiTeam other = new ApiTeam(3, "Other", "OTH", null);
    private final Competition competition = new Competition("PL", "Premier League");
    private final Season season = new Season("2026-08-01");

    private SourceMatch match(long id, String date, String status, ApiTeam a, ApiTeam b, Integer scored, Integer conceded) {
        return new SourceMatch(id, date, status, competition, season, a, b, new ApiScore(new ApiScoreline(scored, conceded)));
    }
    private SourceMatch selected() {
        return match(99, "2026-10-21T15:00:00Z", "TIMED", home, away, null, null);
    }

    @Test
    void excludesSelectedEqualLaterUnfinishedInvalidAndWrongScope() {
        var valid = match(1, "2026-10-20T15:00:00Z", "FINISHED", home, away, 1, 0);
        var wrongCompetition = new SourceMatch(7, valid.utcDate(), "FINISHED", new Competition("CL", "Champions League"), season, home, away, valid.score());
        var wrongSeason = new SourceMatch(8, valid.utcDate(), "FINISHED", competition, new Season("2025-08-01"), home, away, valid.score());
        var fixtures = List.of(valid, selected(),
                match(99, valid.utcDate(), "FINISHED", home, away, 5, 0),
                match(2, selected().utcDate(), "FINISHED", home, away, 5, 0),
                match(3, "2026-10-22T15:00:00Z", "FINISHED", home, away, 5, 0),
                match(4, valid.utcDate(), "IN_PLAY", home, away, 5, 0),
                match(5, "invalid", "FINISHED", home, away, 5, 0), wrongCompetition, wrongSeason);
        assertEquals(List.of(1L), SeasonPerformance.eligible(selected(), fixtures).stream().map(SourceMatch::id).toList());
    }

    @Test
    void calculatesSplitsAndCompetitionAveragesWithCorrectDenominators() {
        var fixtures = List.of(
                match(1, "2026-10-01T15:00:00Z", "FINISHED", home, away, 2, 0),
                match(2, "2026-10-02T15:00:00Z", "FINISHED", away, home, 3, 1),
                match(3, "2026-10-03T15:00:00Z", "FINISHED", home, away, 0, 0),
                match(4, "2026-10-04T15:00:00Z", "FINISHED", home, away, null, 2),
                match(5, "2026-10-05T15:00:00Z", "FINISHED", other, away, 4, 2));
        var response = SeasonPerformance.calculate(selected(), SeasonPerformance.eligible(selected(), fixtures));
        var overall = response.home().overall();
        assertEquals(3, overall.played());
        assertEquals(1, overall.excluded());
        assertEquals(1, overall.wins());
        assertEquals(1, overall.draws());
        assertEquals(1, overall.losses());
        assertEquals(3, overall.goalsScored());
        assertEquals(3, overall.goalsConceded());
        assertEquals(1.0, overall.averageScored());
        assertEquals(2, response.home().home().played());
        assertEquals(1, response.home().home().excluded());
        assertEquals(1.0, response.home().home().averageScored());
        assertEquals(0.0, response.home().home().averageConceded());
        assertEquals(1, response.home().away().played());
        assertEquals(3, response.home().away().goalsConceded());
        assertEquals(3, response.away().away().played());
        assertEquals(2, response.away().away().goalsScored());
        assertEquals(4, response.competition().played());
        assertEquals(1, response.competition().excluded());
        assertEquals(2.25, response.competition().averageHomeGoals());
        assertEquals(0.75, response.competition().averageAwayGoals());
    }

    @Test
    void preservesScorelessDrawsAndHandlesNoDataOrOnlyMissingScores() {
        var zero = SeasonPerformance.calculate(selected(), List.of(match(1, "2026-10-01T15:00:00Z", "FINISHED", home, away, 0, 0)));
        assertEquals(0.0, zero.competition().averageHomeGoals());
        assertEquals(1, zero.home().home().draws());
        assertEquals(0, zero.home().home().goalsScored());
        var empty = SeasonPerformance.calculate(selected(), List.of());
        assertNull(empty.home().overall().averageScored());
        assertNull(empty.home().home().goalsScored());
        assertNull(empty.competition().averageAwayGoals());
        var missing = SeasonPerformance.calculate(selected(), List.of(match(2, "2026-10-01T15:00:00Z", "FINISHED", home, away, 2, null)));
        assertEquals(0, missing.home().overall().played());
        assertEquals(1, missing.home().overall().excluded());
        assertNull(missing.home().overall().goalsScored());
        assertEquals(1, missing.competition().excluded());
        assertNull(missing.competition().averageHomeGoals());
    }
}
