package com.tonyle.footballbettinganalytics.football;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static com.tonyle.footballbettinganalytics.football.FootballDtos.*;
import static com.tonyle.footballbettinganalytics.football.RecentPerformance.*;

class TeamHistoryTest {
    private final ApiTeam home = new ApiTeam(1, "Home", "HOM", null);
    private final ApiTeam away = new ApiTeam(2, "Away", "AWY", null);
    private SourceMatch selected() { return fixture(99, "2026-01-15T15:00:00Z", "CL", true, 1, 1); }
    private SourceMatch fixture(long id, String date, String code, boolean homeVenue, int h, int a) {
        return new SourceMatch(id, date, "FINISHED", new Competition(code, code),
                new Season(date.substring(0, 4) + "-01-01"), homeVenue ? home : away, homeVenue ? away : home,
                new ApiScore(new ApiScoreline(h, a), null, "REGULAR"));
    }
    private TeamHistory.Loaded history(int count) {
        var list = new java.util.ArrayList<SourceMatch>();
        for (int day = 1; day <= count; day++) list.add(fixture(day, "2025-12-%02dT15:00:00Z".formatted(day), day % 2 == 0 ? "PL" : "CL", day % 2 == 0, 2, 1));
        return new TeamHistory.Loaded(list, null);
    }

    @Test
    void selectsTwentyAfterCutoffAcrossSeasonsAndCompetitions() {
        var fixtures = new java.util.ArrayList<>(history(25).matches());
        fixtures.add(fixture(30, "2026-01-15T15:00:00Z", "PL", true, 9, 0));
        fixtures.add(fixture(31, "2026-02-01T15:00:00Z", "PL", true, 9, 0));
        fixtures.add(fixture(32, "2024-12-01T15:00:00Z", "PL", true, 9, 0));
        fixtures.add(fixture(33, "2026-01-14T15:00:00Z", "CL", true, 2, 1));
        var result = TeamHistory.prepare(1, selected(), new TeamHistory.Loaded(fixtures, null));
        assertEquals(20, result.matches().size());
        assertEquals(33, result.matches().getFirst().id());
        assertEquals(7, result.matches().getLast().id());
        assertEquals(2, result.provenance().competitions().size());
        assertEquals(3, result.provenance().outsideWindow());
        assertTrue(result.provenance().firstDate().startsWith("2025"));
        assertTrue(result.provenance().lastDate().startsWith("2026"));
    }

    @Test
    void discardsUnknownScoresAndDuplicatesBeforeTakingTwenty() {
        var fixtures = new java.util.ArrayList<>(history(25).matches());
        fixtures.add(fixtures.getFirst());
        fixtures.add(new SourceMatch(40, "2026-01-14T15:00:00Z", "FINISHED", new Competition("CL", "Champions League"),
                null, home, away, new ApiScore(new ApiScoreline(5, 4), null, "PENALTY_SHOOTOUT")));
        var result = TeamHistory.prepare(1, selected(), new TeamHistory.Loaded(fixtures, null));
        assertEquals(20, result.matches().size());
        assertEquals(1, result.provenance().excludedScores());
        assertEquals(1, result.provenance().duplicates());
        assertEquals(25, result.matches().getFirst().id());
    }

    @Test
    void computesVenueBlendWithoutUnrelatedCompetitionBaseline() {
        var history = history(10);
        var result = PoissonModel.estimate(selected(), history, history);
        assertEquals("available", result.status());
        assertEquals("cross-competition-poisson-v2", result.modelVersion());
        assertEquals(2.0, result.homeExpectedGoals());
        assertEquals(1.0, result.awayExpectedGoals());
        assertEquals(5, result.samples().homeMatches());
        assertEquals(5, result.samples().awayMatches());
        assertTrue(result.warnings().stream().anyMatch(message -> message.contains("Mixed competitions")));
        assertTrue(result.warnings().stream().anyMatch(message -> message.contains("Fewer than 20")));
        assertEquals(1, result.probabilities().homeWin() + result.probabilities().draw() + result.probabilities().awayWin(), 1e-12);
    }

    @Test
    void missingPartialAndSmallHistoriesNeverInventEstimates() {
        var result = PoissonModel.estimate(selected(), history(7), history(10));
        assertEquals("insufficient_data", result.status());
        assertNull(result.probabilities());
        var failure = PoissonModel.estimate(selected(), history(10), TeamHistory.Loaded.failed("Football data rate limit reached."));
        assertEquals("history_unavailable", failure.status());
        assertEquals(10, failure.homeHistory().used());
        assertNotNull(failure.awayHistory().error());
        assertNull(failure.probabilities());
        assertEquals("insufficient_data", PoissonModel.estimate(selected(), history(0), history(0)).status());
    }

    @Test
    void zeroScoresAreRealMeasurementsAndNoBaselineDivisionIsNeeded() {
        var zero = new TeamHistory.Loaded(history(10).matches().stream().map(m -> new SourceMatch(m.id(), m.utcDate(), m.status(), m.competition(), m.season(), m.homeTeam(), m.awayTeam(), new ApiScore(new ApiScoreline(0, 0), null, "REGULAR"))).toList(), null);
        var result = PoissonModel.estimate(selected(), zero, zero);
        assertEquals("available", result.status());
        assertEquals(0, result.homeExpectedGoals());
        assertEquals(1, result.probabilities().draw());
        assertEquals(0, result.probabilities().bttsYes());
    }
}
